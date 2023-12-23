package su.sres.securesms.jobs;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Stream;

import su.sres.securesms.ApplicationContext;
import su.sres.securesms.attachments.Attachment;
import su.sres.securesms.crypto.UnidentifiedAccessUtil;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.MessageDatabase;
import su.sres.securesms.database.MessageDatabase.SyncMessageId;
import su.sres.securesms.database.NoSuchMessageException;
import su.sres.securesms.database.RecipientDatabase.UnidentifiedAccessMode;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobmanager.JobManager;
import su.sres.core.util.logging.Log;
import su.sres.securesms.mms.MmsException;
import su.sres.securesms.mms.OutgoingMediaMessage;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.recipients.RecipientUtil;
import su.sres.securesms.service.ExpiringMessageManager;
import su.sres.securesms.transport.InsecureFallbackApprovalException;
import su.sres.securesms.transport.RetryLaterException;
import su.sres.securesms.transport.UndeliverableMessageException;
import su.sres.securesms.util.TextSecurePreferences;
import su.sres.securesms.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;
import su.sres.signalservice.api.SignalServiceMessageSender;
import su.sres.signalservice.api.crypto.UnidentifiedAccessPair;
import su.sres.signalservice.api.crypto.UntrustedIdentityException;
import su.sres.signalservice.api.messages.SignalServiceAttachment;
import su.sres.signalservice.api.messages.SignalServiceDataMessage;
import su.sres.signalservice.api.messages.SignalServiceDataMessage.Preview;
import su.sres.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import su.sres.signalservice.api.messages.shared.SharedContact;
import su.sres.signalservice.api.push.SignalServiceAddress;
import su.sres.signalservice.api.push.exceptions.ServerRejectedException;
import su.sres.signalservice.api.push.exceptions.UnregisteredUserException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Set;

public class PushMediaSendJob extends PushSendJob  {

  public static final String KEY = "PushMediaSendJob";

  private static final String TAG = Log.tag(PushMediaSendJob.class);

  private static final String KEY_MESSAGE_ID = "message_id";

  private long messageId;

  public PushMediaSendJob(long messageId, @NonNull Recipient recipient) {
    this(constructParameters(recipient, true), messageId);
  }

  private PushMediaSendJob(Job.Parameters parameters, long messageId) {
    super(parameters);
    this.messageId = messageId;
  }

  @WorkerThread
  public static void enqueue(@NonNull Context context, @NonNull JobManager jobManager, long messageId, @NonNull Recipient recipient) {
    try {
      if (!recipient.hasServiceIdentifier()) {
        throw new AssertionError();
      }

      MessageDatabase database            = DatabaseFactory.getMmsDatabase(context);
      OutgoingMediaMessage message             = database.getOutgoingMessage(messageId);
      Set<String> attachmentUploadIds = enqueueCompressingAndUploadAttachmentsChains(jobManager, message);

      jobManager.add(new PushMediaSendJob(messageId, recipient), attachmentUploadIds, recipient.getId().toQueueKey());

    } catch (NoSuchMessageException | MmsException e) {
      Log.w(TAG, "Failed to enqueue message.", e);
      DatabaseFactory.getMmsDatabase(context).markAsSentFailed(messageId);
      notifyMediaMessageDeliveryFailed(context, messageId);
    }
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder().putLong(KEY_MESSAGE_ID, messageId).build();
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
          throws IOException, MmsException, NoSuchMessageException, UndeliverableMessageException
  {
    ExpiringMessageManager expirationManager = ApplicationDependencies.getExpiringMessageManager();
    MessageDatabase        database          = DatabaseFactory.getMmsDatabase(context);
    OutgoingMediaMessage   message           = database.getOutgoingMessage(messageId);
    long                   threadId          = database.getMessageRecord(messageId).getThreadId();

    if (database.isSent(messageId)) {
      warn(TAG, String.valueOf(message.getSentTimeMillis()), "Message " + messageId + " was already sent. Ignoring.");
      return;
    }

    try {
      log(TAG, String.valueOf(message.getSentTimeMillis()), "Sending message: " + messageId + ", Recipient: " + message.getRecipient().getId() + ", Thread: " + threadId);

      RecipientUtil.shareProfileIfFirstSecureMessage(context, message.getRecipient());

      Recipient              recipient  = message.getRecipient().fresh();
      byte[]                 profileKey = recipient.getProfileKey();
      UnidentifiedAccessMode accessMode = recipient.getUnidentifiedAccessMode();

      boolean unidentified = deliver(message);
      database.markAsSent(messageId, true);
      markAttachmentsUploaded(messageId, message);
      database.markUnidentified(messageId, unidentified);

      if (recipient.isSelf()) {
        SyncMessageId id = new SyncMessageId(recipient.getId(), message.getSentTimeMillis());
        DatabaseFactory.getMmsSmsDatabase(context).incrementDeliveryReceiptCount(id, System.currentTimeMillis());
        DatabaseFactory.getMmsSmsDatabase(context).incrementReadReceiptCount(id, System.currentTimeMillis());
        DatabaseFactory.getMmsSmsDatabase(context).incrementViewedReceiptCount(id, System.currentTimeMillis());
      }

      if (unidentified && accessMode == UnidentifiedAccessMode.UNKNOWN && profileKey == null) {
        log(TAG, String.valueOf(message.getSentTimeMillis()), "Marking recipient as UD-unrestricted following a UD send.");
        DatabaseFactory.getRecipientDatabase(context).setUnidentifiedAccessMode(recipient.getId(), UnidentifiedAccessMode.UNRESTRICTED);
      } else if (unidentified && accessMode == UnidentifiedAccessMode.UNKNOWN) {
        log(TAG, String.valueOf(message.getSentTimeMillis()), "Marking recipient as UD-enabled following a UD send.");
        DatabaseFactory.getRecipientDatabase(context).setUnidentifiedAccessMode(recipient.getId(), UnidentifiedAccessMode.ENABLED);
      } else if (!unidentified && accessMode != UnidentifiedAccessMode.DISABLED) {
        log(TAG, String.valueOf(message.getSentTimeMillis()), "Marking recipient as UD-disabled following a non-UD send.");

        DatabaseFactory.getRecipientDatabase(context).setUnidentifiedAccessMode(recipient.getId(), UnidentifiedAccessMode.DISABLED);
      }

      if (message.getExpiresIn() > 0 && !message.isExpirationUpdate()) {
        database.markExpireStarted(messageId);
        expirationManager.scheduleDeletion(messageId, true, message.getExpiresIn());
      }

      if (message.isViewOnce()) {
        DatabaseFactory.getAttachmentDatabase(context).deleteAttachmentFilesForViewOnceMessage(messageId);
      }

      log(TAG, String.valueOf(message.getSentTimeMillis()), "Sent message: " + messageId);

    } catch (InsecureFallbackApprovalException ifae) {
      warn(TAG, "Failure", ifae);
      database.markAsPendingInsecureSmsFallback(messageId);
      notifyMediaMessageDeliveryFailed(context, messageId);
      // ApplicationDependencies.getJobManager().add(new DirectoryRefreshJob(false));
      ApplicationDependencies.getJobManager().add(new DirectorySyncJob(false));
    } catch (UntrustedIdentityException uie) {
      warn(TAG, "Failure", uie);
      RecipientId recipientId = Recipient.external(context, uie.getIdentifier()).getId();
      database.addMismatchedIdentity(messageId, recipientId, uie.getIdentityKey());
      database.markAsSentFailed(messageId);
      RetrieveProfileJob.enqueue(recipientId);
    }
  }

  @Override
  public void onFailure() {
    DatabaseFactory.getMmsDatabase(context).markAsSentFailed(messageId);
    notifyMediaMessageDeliveryFailed(context, messageId);
  }

  private boolean deliver(OutgoingMediaMessage message)
          throws IOException, InsecureFallbackApprovalException, UntrustedIdentityException, UndeliverableMessageException
  {
    if (message.getRecipient() == null) {
      throw new UndeliverableMessageException("No destination address.");
    }

    try {
      rotateSenderCertificateIfNecessary();

      Recipient                                  messageRecipient   = message.getRecipient().fresh();
      SignalServiceMessageSender                 messageSender      = ApplicationDependencies.getSignalServiceMessageSender();
      SignalServiceAddress                       address            = RecipientUtil.toSignalServiceAddress(context, messageRecipient);
      List<Attachment>                           attachments        = Stream.of(message.getAttachments()).filterNot(Attachment::isSticker).toList();
      List<SignalServiceAttachment>              serviceAttachments = getAttachmentPointersFor(attachments);
      Optional<byte[]>                           profileKey         = getProfileKey(messageRecipient);
      Optional<SignalServiceDataMessage.Quote>   quote              = getQuoteFor(message);
      Optional<SignalServiceDataMessage.Sticker> sticker            = getStickerFor(message);
      List<SharedContact>                        sharedContacts     = getSharedContactsFor(message);
      List<Preview>                              previews           = getPreviewsFor(message);
      SignalServiceDataMessage                   mediaMessage       = SignalServiceDataMessage.newBuilder()
              .withBody(message.getBody())
              .withAttachments(serviceAttachments)
              .withTimestamp(message.getSentTimeMillis())
              .withExpiration((int)(message.getExpiresIn() / 1000))
              .withViewOnce(message.isViewOnce())
              .withProfileKey(profileKey.orNull())
              .withQuote(quote.orNull())
              .withSticker(sticker.orNull())
              .withSharedContacts(sharedContacts)
              .withPreviews(previews)
              .asExpirationUpdate(message.isExpirationUpdate())
              .build();

      if (Util.equals(TextSecurePreferences.getLocalUuid(context), address.getUuid().orNull())) {
        Optional<UnidentifiedAccessPair> syncAccess  = UnidentifiedAccessUtil.getAccessForSync(context);
        SignalServiceSyncMessage         syncMessage = buildSelfSendSyncMessage(context, mediaMessage, syncAccess);

        messageSender.sendMessage(syncMessage, syncAccess);
        return syncAccess.isPresent();
      } else {
        return messageSender.sendMessage(address, UnidentifiedAccessUtil.getAccessFor(context, messageRecipient), mediaMessage).getSuccess().isUnidentified();
      }
    } catch (UnregisteredUserException e) {
      warn(TAG, String.valueOf(message.getSentTimeMillis()), e);
      throw new InsecureFallbackApprovalException(e);
    } catch (FileNotFoundException e) {
      warn(TAG, String.valueOf(message.getSentTimeMillis()), e);
      throw new UndeliverableMessageException(e);
    } catch (ServerRejectedException e) {
      throw new UndeliverableMessageException(e);
    }
  }

  public static final class Factory implements Job.Factory<PushMediaSendJob> {
    @Override
    public @NonNull PushMediaSendJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new PushMediaSendJob(parameters, data.getLong(KEY_MESSAGE_ID));
    }
  }
}
