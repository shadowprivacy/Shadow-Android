package su.sres.securesms.jobs;

import androidx.annotation.NonNull;

import su.sres.securesms.database.MessageDatabase;
import su.sres.securesms.database.MessageDatabase.SyncMessageId;
import su.sres.securesms.database.RecipientDatabase.UnidentifiedAccessMode;

import su.sres.securesms.ApplicationContext;
import su.sres.securesms.crypto.UnidentifiedAccessUtil;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.NoSuchMessageException;
import su.sres.securesms.database.SmsDatabase;
import su.sres.securesms.database.model.SmsMessageRecord;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.recipients.RecipientUtil;
import su.sres.securesms.service.ExpiringMessageManager;
import su.sres.securesms.transport.InsecureFallbackApprovalException;
import su.sres.securesms.transport.RetryLaterException;
import su.sres.securesms.util.TextSecurePreferences;
import su.sres.securesms.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;
import su.sres.signalservice.api.SignalServiceMessageSender;
import su.sres.signalservice.api.crypto.UnidentifiedAccessPair;
import su.sres.signalservice.api.crypto.UntrustedIdentityException;
import su.sres.signalservice.api.messages.SignalServiceDataMessage;
import su.sres.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import su.sres.signalservice.api.push.SignalServiceAddress;
import su.sres.signalservice.api.push.exceptions.UnregisteredUserException;

import java.io.IOException;

public class PushTextSendJob extends PushSendJob  {

  public static final String KEY = "PushTextSendJob";

  private static final String TAG = PushTextSendJob.class.getSimpleName();

  private static final String KEY_MESSAGE_ID = "message_id";

  private long messageId;

  public PushTextSendJob(long messageId, @NonNull Recipient recipient) {
    this(constructParameters(recipient, false), messageId);
  }

  private PushTextSendJob(@NonNull Job.Parameters parameters, long messageId) {
    super(parameters);
    this.messageId = messageId;
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
    DatabaseFactory.getSmsDatabase(context).markAsSending(messageId);
  }

  @Override
  public void onPushSend() throws NoSuchMessageException, RetryLaterException {
    ExpiringMessageManager expirationManager = ApplicationContext.getInstance(context).getExpiringMessageManager();
    MessageDatabase database          = DatabaseFactory.getSmsDatabase(context);
    SmsMessageRecord       record            = database.getSmsMessage(messageId);

    if (!record.isPending() && !record.isFailed()) {
      warn(TAG, String.valueOf(record.getDateSent()), "Message " + messageId + " was already sent. Ignoring.");
      return;
    }

    try {
      log(TAG, String.valueOf(record.getDateSent()), "Sending message: " + messageId);

      RecipientUtil.shareProfileIfFirstSecureMessage(context, record.getRecipient());

      Recipient              recipient  = record.getRecipient().fresh();
      byte[]                 profileKey = recipient.getProfileKey();
      UnidentifiedAccessMode accessMode = recipient.getUnidentifiedAccessMode();

      boolean unidentified = deliver(record);
      database.markAsSent(messageId, true);
      database.markUnidentified(messageId, unidentified);

      if (recipient.isLocalNumber()) {
        SyncMessageId id = new SyncMessageId(recipient.getId(), record.getDateSent());
        DatabaseFactory.getMmsSmsDatabase(context).incrementDeliveryReceiptCount(id, System.currentTimeMillis());
        DatabaseFactory.getMmsSmsDatabase(context).incrementReadReceiptCount(id, System.currentTimeMillis());
      }

        if (unidentified && accessMode == UnidentifiedAccessMode.UNKNOWN && profileKey == null) {
          log(TAG, String.valueOf(record.getDateSent()), "Marking recipient as UD-unrestricted following a UD send.");
            DatabaseFactory.getRecipientDatabase(context).setUnidentifiedAccessMode(recipient.getId(), UnidentifiedAccessMode.UNRESTRICTED);
        } else if (unidentified && accessMode == UnidentifiedAccessMode.UNKNOWN) {
          log(TAG, String.valueOf(record.getDateSent()), "Marking recipient as UD-enabled following a UD send.");
            DatabaseFactory.getRecipientDatabase(context).setUnidentifiedAccessMode(recipient.getId(), UnidentifiedAccessMode.ENABLED);
        } else if (!unidentified && accessMode != UnidentifiedAccessMode.DISABLED) {
          log(TAG, String.valueOf(record.getDateSent()), "Marking recipient as UD-disabled following a non-UD send.");
            DatabaseFactory.getRecipientDatabase(context).setUnidentifiedAccessMode(recipient.getId(), UnidentifiedAccessMode.DISABLED);
      }

      if (record.getExpiresIn() > 0) {
        database.markExpireStarted(messageId);
        expirationManager.scheduleDeletion(record.getId(), record.isMms(), record.getExpiresIn());
      }

      log(TAG, String.valueOf(record.getDateSent()), "Sent message: " + messageId);

    } catch (InsecureFallbackApprovalException e) {
      warn(TAG, String.valueOf(record.getDateSent()), "Failure", e);
      database.markAsPendingInsecureSmsFallback(record.getId());
      ApplicationDependencies.getMessageNotifier().notifyMessageDeliveryFailed(context, record.getRecipient(), record.getThreadId());
      ApplicationDependencies.getJobManager().add(new DirectorySyncJob(false));
    } catch (UntrustedIdentityException e) {
      warn(TAG, String.valueOf(record.getDateSent()), "Failure", e);
      RecipientId recipientId = Recipient.external(context, e.getIdentifier()).getId();
      database.addMismatchedIdentity(record.getId(), recipientId, e.getIdentityKey());
      database.markAsSentFailed(record.getId());
      database.markAsPush(record.getId());
      RetrieveProfileJob.enqueue(recipientId);
    }
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception exception) {
    if (exception instanceof RetryLaterException) return true;

    return false;
  }

  @Override
  public void onFailure() {
    DatabaseFactory.getSmsDatabase(context).markAsSentFailed(messageId);

    long      threadId  = DatabaseFactory.getSmsDatabase(context).getThreadIdForMessage(messageId);
    Recipient recipient = DatabaseFactory.getThreadDatabase(context).getRecipientForThreadId(threadId);

    if (threadId != -1 && recipient != null) {
      ApplicationDependencies.getMessageNotifier().notifyMessageDeliveryFailed(context, recipient, threadId);
    }
  }

  private boolean deliver(SmsMessageRecord message)
      throws UntrustedIdentityException, InsecureFallbackApprovalException, RetryLaterException
  {
    try {
      rotateSenderCertificateIfNecessary();

      Recipient                        messageRecipient   = message.getIndividualRecipient().fresh();
      SignalServiceMessageSender       messageSender      = ApplicationDependencies.getSignalServiceMessageSender();
      SignalServiceAddress             address            = RecipientUtil.toSignalServiceAddress(context, messageRecipient);
      Optional<byte[]>                 profileKey         = getProfileKey(messageRecipient);
      Optional<UnidentifiedAccessPair> unidentifiedAccess = UnidentifiedAccessUtil.getAccessFor(context, messageRecipient);

      log(TAG, String.valueOf(message.getDateSent()), "Have access key to use: " + unidentifiedAccess.isPresent());

      SignalServiceDataMessage textSecureMessage = SignalServiceDataMessage.newBuilder()
              .withTimestamp(message.getDateSent())
              .withBody(message.getBody())
              .withExpiration((int)(message.getExpiresIn() / 1000))
              .withProfileKey(profileKey.orNull())
              .asEndSessionMessage(message.isEndSession())
              .build();

      if (Util.equals(TextSecurePreferences.getLocalUuid(context), address.getUuid().orNull())) {
        Optional<UnidentifiedAccessPair> syncAccess  = UnidentifiedAccessUtil.getAccessForSync(context);
        SignalServiceSyncMessage         syncMessage = buildSelfSendSyncMessage(context, textSecureMessage, syncAccess);

        messageSender.sendMessage(syncMessage, syncAccess);
        return syncAccess.isPresent();
      } else {
        return messageSender.sendMessage(address, unidentifiedAccess, textSecureMessage).getSuccess().isUnidentified();
      }
    } catch (UnregisteredUserException e) {
      warn(TAG, "Failure", e);
      throw new InsecureFallbackApprovalException(e);
    } catch (IOException e) {
      warn(TAG, "Failure", e);
      throw new RetryLaterException(e);
    }
  }

  public static class Factory implements Job.Factory<PushTextSendJob> {
    @Override
    public @NonNull PushTextSendJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new PushTextSendJob(parameters, data.getLong(KEY_MESSAGE_ID));
    }
  }
}
