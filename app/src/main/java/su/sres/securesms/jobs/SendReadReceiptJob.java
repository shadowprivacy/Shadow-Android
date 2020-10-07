package su.sres.securesms.jobs;

import android.app.Application;

import androidx.annotation.NonNull;

import su.sres.securesms.crypto.UnidentifiedAccessUtil;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobmanager.impl.NetworkConstraint;
import su.sres.securesms.logging.Log;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.recipients.RecipientUtil;
import su.sres.securesms.util.TextSecurePreferences;
import su.sres.signalservice.api.SignalServiceMessageSender;
import su.sres.signalservice.api.crypto.UntrustedIdentityException;
import su.sres.signalservice.api.messages.SignalServiceReceiptMessage;
import su.sres.signalservice.api.push.SignalServiceAddress;
import su.sres.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SendReadReceiptJob extends BaseJob  {

  public static final String KEY = "SendReadReceiptJob";

  private static final String TAG = SendReadReceiptJob.class.getSimpleName();

  private static final String KEY_THREAD      = "thread";
  private static final String KEY_ADDRESS     = "address";
  private static final String KEY_RECIPIENT   = "recipient";
  private static final String KEY_MESSAGE_IDS = "message_ids";
  private static final String KEY_TIMESTAMP   = "timestamp";

  private long        threadId;
  private RecipientId recipientId;
  private List<Long>  messageIds;
  private long        timestamp;

  public SendReadReceiptJob(long threadId, @NonNull RecipientId recipientId, List<Long> messageIds) {
    this(new Job.Parameters.Builder()
                    .addConstraint(NetworkConstraint.KEY)
                    .setLifespan(TimeUnit.DAYS.toMillis(1))
                    .setMaxAttempts(Parameters.UNLIMITED)
                    .build(),
            threadId,
            recipientId,
            messageIds,
            System.currentTimeMillis());
  }

  private SendReadReceiptJob(@NonNull Job.Parameters parameters,
                             long threadId,
                             @NonNull RecipientId recipientId,
                             @NonNull List<Long> messageIds,
                             long timestamp)
  {
    super(parameters);

    this.threadId    = threadId;
    this.recipientId = recipientId;
    this.messageIds  = messageIds;
    this.timestamp   = timestamp;
  }

  @Override
  public @NonNull Data serialize() {
    long[] ids = new long[messageIds.size()];
    for (int i = 0; i < ids.length; i++) {
      ids[i] = messageIds.get(i);
    }

    return new Data.Builder().putString(KEY_RECIPIENT, recipientId.serialize())
            .putLongArray(KEY_MESSAGE_IDS, ids)
            .putLong(KEY_TIMESTAMP, timestamp)
            .putLong(KEY_THREAD, threadId)
            .build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws IOException, UntrustedIdentityException {
    if (!TextSecurePreferences.isReadReceiptsEnabled(context) || messageIds.isEmpty()) return;

    if (!RecipientUtil.isMessageRequestAccepted(context, threadId)) {
      Log.w(TAG, "Refusing to send receipts to untrusted recipient");
      return;
    }

    Recipient recipient = Recipient.resolved(recipientId);
    if (recipient.isBlocked()) {
      Log.w(TAG, "Refusing to send receipts to blocked recipient");
      return;
    }

    SignalServiceMessageSender  messageSender  = ApplicationDependencies.getSignalServiceMessageSender();
    SignalServiceAddress        remoteAddress  = RecipientUtil.toSignalServiceAddress(context, recipient);
    SignalServiceReceiptMessage receiptMessage = new SignalServiceReceiptMessage(SignalServiceReceiptMessage.Type.READ, messageIds, timestamp);

    messageSender.sendReceipt(remoteAddress,
            UnidentifiedAccessUtil.getAccessFor(context, Recipient.resolved(recipientId)),
            receiptMessage);
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception e) {
    if (e instanceof PushNetworkException) return true;
    return false;
  }

  @Override
  public void onFailure() {
    Log.w(TAG, "Failed to send read receipts to: " + recipientId);
  }

  public static final class Factory implements Job.Factory<SendReadReceiptJob> {

    private final Application application;

    public Factory(@NonNull Application application) {
      this.application = application;
    }

    @Override
    public @NonNull SendReadReceiptJob create(@NonNull Parameters parameters, @NonNull Data data) {
      long        timestamp   = data.getLong(KEY_TIMESTAMP);
      long[]      ids         = data.hasLongArray(KEY_MESSAGE_IDS) ? data.getLongArray(KEY_MESSAGE_IDS) : new long[0];
      List<Long>  messageIds  = new ArrayList<>(ids.length);
      RecipientId recipientId = data.hasString(KEY_RECIPIENT) ? RecipientId.from(data.getString(KEY_RECIPIENT))
              : Recipient.external(application, data.getString(KEY_ADDRESS)).getId();

      long        threadId    = data.getLong(KEY_THREAD);

      for (long id : ids) {
        messageIds.add(id);
      }

      return new SendReadReceiptJob(parameters, threadId, recipientId, messageIds, timestamp);
    }
  }
}
