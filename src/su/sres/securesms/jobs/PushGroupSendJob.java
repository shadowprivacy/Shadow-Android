package su.sres.securesms.jobs;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import su.sres.securesms.ApplicationContext;
import su.sres.securesms.attachments.Attachment;
import su.sres.securesms.attachments.DatabaseAttachment;
import su.sres.securesms.crypto.UnidentifiedAccessUtil;
import su.sres.securesms.database.Address;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.GroupReceiptDatabase.GroupReceiptInfo;
import su.sres.securesms.database.MmsDatabase;
import su.sres.securesms.database.NoSuchMessageException;
import su.sres.securesms.database.documents.IdentityKeyMismatch;
import su.sres.securesms.database.documents.NetworkFailure;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobmanager.JobManager;
import su.sres.securesms.jobmanager.impl.NetworkConstraint;
import su.sres.securesms.logging.Log;
import su.sres.securesms.mms.MmsException;
import su.sres.securesms.mms.OutgoingGroupMediaMessage;
import su.sres.securesms.mms.OutgoingMediaMessage;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.transport.RetryLaterException;
import su.sres.securesms.transport.UndeliverableMessageException;
import su.sres.securesms.util.GroupUtil;
import org.whispersystems.libsignal.util.guava.Optional;
import su.sres.signalservice.api.SignalServiceMessageSender;
import su.sres.signalservice.api.crypto.UnidentifiedAccessPair;
import su.sres.signalservice.api.crypto.UntrustedIdentityException;
import su.sres.signalservice.api.messages.SendMessageResult;
import su.sres.signalservice.api.messages.SignalServiceAttachment;
import su.sres.signalservice.api.messages.SignalServiceDataMessage;
import su.sres.signalservice.api.messages.SignalServiceDataMessage.Preview;
import su.sres.signalservice.api.messages.SignalServiceDataMessage.Quote;
import su.sres.signalservice.api.messages.SignalServiceGroup;
import su.sres.signalservice.api.messages.shared.SharedContact;
import su.sres.signalservice.api.push.SignalServiceAddress;
import su.sres.signalservice.internal.push.SignalServiceProtos.GroupContext;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;



public class PushGroupSendJob extends PushSendJob  {

  public static final String KEY = "PushGroupSendJob";

  private static final String TAG = PushGroupSendJob.class.getSimpleName();



  private static final String KEY_MESSAGE_ID     = "message_id";
  private static final String KEY_FILTER_ADDRESS = "filter_address";

  private long   messageId;
  private String filterAddress;

  public PushGroupSendJob(long messageId, @NonNull Address destination, @Nullable Address filterAddress) {
    this(new Job.Parameters.Builder()
                    .setQueue(destination.toGroupString())
                    .addConstraint(NetworkConstraint.KEY)
                    .setLifespan(TimeUnit.DAYS.toMillis(1))
                    .setMaxAttempts(Parameters.UNLIMITED)
                    .build(),
            messageId, filterAddress);

  }

  private PushGroupSendJob(@NonNull Job.Parameters parameters, long messageId, @Nullable Address filterAddress) {
    super(parameters);

    this.messageId     = messageId;
    this.filterAddress = filterAddress == null ? null :filterAddress.toPhoneString();
  }

  @WorkerThread
  public static void enqueue(@NonNull Context context, @NonNull JobManager jobManager, long messageId, @NonNull Address destination, @Nullable Address filterAddress) {
    try {
      MmsDatabase          database    = DatabaseFactory.getMmsDatabase(context);
      OutgoingMediaMessage message     = database.getOutgoingMessage(messageId);
      List<Attachment>     attachments = new LinkedList<>();

      attachments.addAll(message.getAttachments());
      attachments.addAll(Stream.of(message.getLinkPreviews()).filter(p -> p.getThumbnail().isPresent()).map(p -> p.getThumbnail().get()).toList());
      attachments.addAll(Stream.of(message.getSharedContacts()).filter(c -> c.getAvatar() != null).map(c -> c.getAvatar().getAttachment()).withoutNulls().toList());

      List<AttachmentUploadJob> attachmentJobs = Stream.of(attachments).map(a -> new AttachmentUploadJob(((DatabaseAttachment) a).getAttachmentId())).toList();

      if (attachmentJobs.isEmpty()) {
        jobManager.add(new PushGroupSendJob(messageId, destination, filterAddress));
      } else {
        jobManager.startChain(attachmentJobs)
                .then(new PushGroupSendJob(messageId, destination, filterAddress))
                .enqueue();
      }

    } catch (NoSuchMessageException | MmsException e) {
      Log.w(TAG, "Failed to enqueue message.", e);
      DatabaseFactory.getMmsDatabase(context).markAsSentFailed(messageId);
      notifyMediaMessageDeliveryFailed(context, messageId);
    }
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder().putLong(KEY_MESSAGE_ID, messageId)
            .putString(KEY_FILTER_ADDRESS, filterAddress)
            .build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onAdded() {
    DatabaseFactory.getMmsDatabase(context).markAsSending(messageId);
  }

  @Override
  public void onPushSend()
          throws IOException, MmsException, NoSuchMessageException,  RetryLaterException

  {
    MmsDatabase               database                   = DatabaseFactory.getMmsDatabase(context);
    OutgoingMediaMessage      message                    = database.getOutgoingMessage(messageId);
    List<NetworkFailure>      existingNetworkFailures    = message.getNetworkFailures();
    List<IdentityKeyMismatch> existingIdentityMismatches = message.getIdentityKeyMismatches();

    if (database.isSent(messageId)) {
      log(TAG, "Message " + messageId + " was already sent. Ignoring.");
      return;
    }

    try {
      log(TAG, "Sending message: " + messageId);

      List<Address> target;

      if      (filterAddress != null)              target = Collections.singletonList(Address.fromSerialized(filterAddress));
      else if (!existingNetworkFailures.isEmpty()) target = Stream.of(existingNetworkFailures).map(NetworkFailure::getAddress).toList();
      else                                         target = getGroupMessageRecipients(message.getRecipient().getAddress().toGroupString(), messageId);

      List<SendMessageResult>   results                  = deliver(message, target);
      List<NetworkFailure>      networkFailures          = Stream.of(results).filter(SendMessageResult::isNetworkFailure).map(result -> new NetworkFailure(Address.fromSerialized(result.getAddress().getNumber()))).toList();
      List<IdentityKeyMismatch> identityMismatches       = Stream.of(results).filter(result -> result.getIdentityFailure() != null).map(result -> new IdentityKeyMismatch(Address.fromSerialized(result.getAddress().getNumber()), result.getIdentityFailure().getIdentityKey())).toList();
      Set<Address>              successAddresses         = Stream.of(results).filter(result -> result.getSuccess() != null).map(result -> Address.fromSerialized(result.getAddress().getNumber())).collect(Collectors.toSet());
      List<NetworkFailure>      resolvedNetworkFailures  = Stream.of(existingNetworkFailures).filter(failure -> successAddresses.contains(failure.getAddress())).toList();
      List<IdentityKeyMismatch> resolvedIdentityFailures = Stream.of(existingIdentityMismatches).filter(failure -> successAddresses.contains(failure.getAddress())).toList();
      List<SendMessageResult>   successes                = Stream.of(results).filter(result -> result.getSuccess() != null).toList();

      for (NetworkFailure resolvedFailure : resolvedNetworkFailures) {
        database.removeFailure(messageId, resolvedFailure);
        existingNetworkFailures.remove(resolvedFailure);
      }

      for (IdentityKeyMismatch resolvedIdentity : resolvedIdentityFailures) {
        database.removeMismatchedIdentity(messageId, resolvedIdentity.getAddress(), resolvedIdentity.getIdentityKey());
        existingIdentityMismatches.remove(resolvedIdentity);
      }

      if (!networkFailures.isEmpty()) {
        database.addFailures(messageId, networkFailures);
      }

      for (IdentityKeyMismatch mismatch : identityMismatches) {
        database.addMismatchedIdentity(messageId, mismatch.getAddress(), mismatch.getIdentityKey());
      }

      for (SendMessageResult success : successes) {
        DatabaseFactory.getGroupReceiptDatabase(context).setUnidentified(Address.fromSerialized(success.getAddress().getNumber()),
                messageId,
                success.getSuccess().isUnidentified());
      }

      if (existingNetworkFailures.isEmpty() && networkFailures.isEmpty() && identityMismatches.isEmpty() && existingIdentityMismatches.isEmpty()) {
        database.markAsSent(messageId, true);

        markAttachmentsUploaded(messageId, message.getAttachments());

        if (message.getExpiresIn() > 0 && !message.isExpirationUpdate()) {
          database.markExpireStarted(messageId);
          ApplicationContext.getInstance(context)
                            .getExpiringMessageManager()
                            .scheduleDeletion(messageId, true, message.getExpiresIn());
        }

        if (message.getRevealDuration() > 0) {
          database.markRevealStarted(messageId);
          ApplicationContext.getInstance(context)
                  .getRevealableMessageManager()
                  .scheduleIfNecessary();
        }

      } else if (!networkFailures.isEmpty()) {
        throw new RetryLaterException();
      } else if (!identityMismatches.isEmpty()) {
        database.markAsSentFailed(messageId);
        notifyMediaMessageDeliveryFailed(context, messageId);
      }

    } catch (UntrustedIdentityException | UndeliverableMessageException e) {
      warn(TAG, e);
      database.markAsSentFailed(messageId);
      notifyMediaMessageDeliveryFailed(context, messageId);
    }
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception exception) {
    if (exception instanceof IOException)         return true;
    if (exception instanceof RetryLaterException) return true;
    return false;
  }

  @Override
  public void onCanceled() {
    DatabaseFactory.getMmsDatabase(context).markAsSentFailed(messageId);
  }

  private List<SendMessageResult> deliver(OutgoingMediaMessage message, @NonNull List<Address> destinations)
          throws IOException, UntrustedIdentityException, UndeliverableMessageException {
    rotateSenderCertificateIfNecessary();

    SignalServiceMessageSender                 messageSender      = ApplicationDependencies.getSignalServiceMessageSender();
    String                                     groupId            = message.getRecipient().getAddress().toGroupString();
    Optional<byte[]>                           profileKey         = getProfileKey(message.getRecipient());
    Optional<Quote>                            quote              = getQuoteFor(message);
    Optional<SignalServiceDataMessage.Sticker> sticker            = getStickerFor(message);
    List<SharedContact>                        sharedContacts     = getSharedContactsFor(message);
    List<Preview>                              previews           = getPreviewsFor(message);
    List<SignalServiceAddress>                 addresses          = Stream.of(destinations).map(this::getPushAddress).toList();
    List<Attachment>                           attachments        = Stream.of(message.getAttachments()).filterNot(Attachment::isSticker).toList();
    List<SignalServiceAttachment>              attachmentPointers = getAttachmentPointersFor(attachments);
    boolean                                    isRecipientUpdate  = destinations.size() != DatabaseFactory.getGroupReceiptDatabase(context).getGroupReceiptInfo(messageId).size();

    List<Optional<UnidentifiedAccessPair>> unidentifiedAccess = Stream.of(addresses)
            .map(address -> Address.fromSerialized(address.getNumber()))
            .map(address -> Recipient.from(context, address, false))
            .map(recipient -> UnidentifiedAccessUtil.getAccessFor(context, recipient))
            .toList();

    if (message.isGroup()) {

      OutgoingGroupMediaMessage groupMessage     = (OutgoingGroupMediaMessage) message;
      GroupContext              groupContext     = groupMessage.getGroupContext();
      SignalServiceAttachment   avatar           = attachmentPointers.isEmpty() ? null : attachmentPointers.get(0);
      SignalServiceGroup.Type   type             = groupMessage.isGroupQuit() ? SignalServiceGroup.Type.QUIT : SignalServiceGroup.Type.UPDATE;
      SignalServiceGroup        group            = new SignalServiceGroup(type, GroupUtil.getDecodedId(groupId), groupContext.getName(), groupContext.getMembersList(), avatar);
      SignalServiceDataMessage  groupDataMessage = SignalServiceDataMessage.newBuilder()
                                                                           .withTimestamp(message.getSentTimeMillis())
                                                                           .withExpiration(message.getRecipient().getExpireMessages())
                                                                           .asGroupMessage(group)
                                                                           .build();

      return messageSender.sendMessage(addresses, unidentifiedAccess, isRecipientUpdate, groupDataMessage);
    } else {

      SignalServiceGroup       group        = new SignalServiceGroup(GroupUtil.getDecodedId(groupId));
      SignalServiceDataMessage groupMessage = SignalServiceDataMessage.newBuilder()
                                                                      .withTimestamp(message.getSentTimeMillis())
                                                                      .asGroupMessage(group)
                                                                      .withAttachments(attachmentPointers)
                                                                      .withBody(message.getBody())
                                                                      .withExpiration((int)(message.getExpiresIn() / 1000))
              .withMessageTimer((int)(message.getRevealDuration() / 1000))
                                                                      .asExpirationUpdate(message.isExpirationUpdate())
                                                                      .withProfileKey(profileKey.orNull())
                                                                      .withQuote(quote.orNull())
                                                                      .withSticker(sticker.orNull())
                                                                      .withSharedContacts(sharedContacts)
                                                                      .withPreviews(previews)
                                                                      .build();

      return messageSender.sendMessage(addresses, unidentifiedAccess, isRecipientUpdate, groupMessage);
    }
  }

  private @NonNull List<Address> getGroupMessageRecipients(String groupId, long messageId) {
    List<GroupReceiptInfo> destinations = DatabaseFactory.getGroupReceiptDatabase(context).getGroupReceiptInfo(messageId);
    if (!destinations.isEmpty()) return Stream.of(destinations).map(GroupReceiptInfo::getAddress).toList();

    List<Recipient> members = DatabaseFactory.getGroupDatabase(context).getGroupMembers(groupId, false);
    return Stream.of(members).map(Recipient::getAddress).toList();
  }

  public static class Factory implements Job.Factory<PushGroupSendJob> {
    @Override
    public @NonNull PushGroupSendJob create(@NonNull Parameters parameters, @NonNull su.sres.securesms.jobmanager.Data data) {
      String  address = data.getString(KEY_FILTER_ADDRESS);
      Address filter  = address != null ? Address.fromSerialized(data.getString(KEY_FILTER_ADDRESS)) : null;

      return new PushGroupSendJob(parameters, data.getLong(KEY_MESSAGE_ID), filter);
    }
  }
}
