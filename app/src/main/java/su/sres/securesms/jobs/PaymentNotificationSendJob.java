package su.sres.securesms.jobs;

import androidx.annotation.NonNull;

import su.sres.core.util.logging.Log;
import su.sres.securesms.crypto.UnidentifiedAccessUtil;
import su.sres.securesms.database.PaymentDatabase;
import su.sres.securesms.database.ShadowDatabase;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.net.NotPushRegisteredException;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.recipients.RecipientUtil;
import su.sres.securesms.transport.RetryLaterException;
import org.whispersystems.libsignal.util.guava.Optional;
import su.sres.signalservice.api.SignalServiceMessageSender;
import su.sres.signalservice.api.SignalServiceMessageSender.IndividualSendEvents;
import su.sres.signalservice.api.crypto.ContentHint;
import su.sres.signalservice.api.crypto.UnidentifiedAccessPair;
import su.sres.signalservice.api.messages.SendMessageResult;
import su.sres.signalservice.api.messages.SignalServiceDataMessage;
import su.sres.signalservice.api.push.SignalServiceAddress;
import su.sres.signalservice.api.push.exceptions.ServerRejectedException;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class PaymentNotificationSendJob extends BaseJob {

  public static final String KEY = "PaymentNotificationSendJob";

  private static final String TAG = Log.tag(PaymentNotificationSendJob.class);

  private static final String KEY_UUID      = "uuid";
  private static final String KEY_RECIPIENT = "recipient";

  private final RecipientId recipientId;
  private final UUID        uuid;

  PaymentNotificationSendJob(@NonNull RecipientId recipientId,
                             @NonNull UUID uuid,
                             @NonNull String queue)
  {
    this(new Parameters.Builder()
                       .setQueue(queue)
                       .setLifespan(TimeUnit.DAYS.toMillis(1))
                       .setMaxAttempts(Parameters.UNLIMITED)
                       .build(),
         recipientId,
         uuid);
  }

  private PaymentNotificationSendJob(@NonNull Parameters parameters,
                                     @NonNull RecipientId recipientId,
                                     @NonNull UUID uuid)
  {
    super(parameters);

    this.recipientId = recipientId;
    this.uuid        = uuid;
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder()
                   .putString(KEY_RECIPIENT, recipientId.serialize())
                   .putString(KEY_UUID, uuid.toString())
                   .build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  protected void onRun() throws Exception {
    if (!Recipient.self().isRegistered()) {
      throw new NotPushRegisteredException();
    }

    PaymentDatabase paymentDatabase = ShadowDatabase.payments();
    Recipient       recipient       = Recipient.resolved(recipientId);

    if (recipient.isUnregistered()) {
      Log.w(TAG, recipientId + " not registered!");
      return;
    }

    SignalServiceMessageSender       messageSender      = ApplicationDependencies.getSignalServiceMessageSender();
    SignalServiceAddress             address            = RecipientUtil.toSignalServiceAddress(context, recipient);
    Optional<UnidentifiedAccessPair> unidentifiedAccess = UnidentifiedAccessUtil.getAccessFor(context, recipient);

    PaymentDatabase.PaymentTransaction payment = paymentDatabase.getPayment(uuid);

    if (payment == null) {
      Log.w(TAG, "Could not find payment, cannot send notification " + uuid);
      return;
    }

    if (payment.getReceipt() == null) {
      Log.w(TAG, "Could not find payment receipt, cannot send notification " + uuid);
      return;
    }

    SignalServiceDataMessage dataMessage = SignalServiceDataMessage.newBuilder()
                                                                   .withPayment(new SignalServiceDataMessage.Payment(new SignalServiceDataMessage.PaymentNotification(payment.getReceipt(), payment.getNote())))
                                                                   .build();

    SendMessageResult sendMessageResult = messageSender.sendDataMessage(address, unidentifiedAccess, ContentHint.DEFAULT, dataMessage, IndividualSendEvents.EMPTY);

    if (sendMessageResult.getIdentityFailure() != null) {
      Log.w(TAG, "Identity failure for " + recipient.getId());
    } else if (sendMessageResult.isUnregisteredFailure()) {
      Log.w(TAG, "Unregistered failure for " + recipient.getId());
    } else if (sendMessageResult.getSuccess() == null) {
      throw new RetryLaterException();
    } else {
      Log.i(TAG, String.format("Payment notification sent to %s for %s", recipientId, uuid));
    }
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    if (e instanceof ServerRejectedException) return false;
    if (e instanceof NotPushRegisteredException) return false;
    return e instanceof IOException ||
           e instanceof RetryLaterException;
  }

  @Override
  public void onFailure() {
    Log.w(TAG, String.format("Failed to send payment notification to recipient %s for %s", recipientId, uuid));
  }

  public static class Factory implements Job.Factory<PaymentNotificationSendJob> {
    @Override
    public @NonNull PaymentNotificationSendJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new PaymentNotificationSendJob(parameters,
                                            RecipientId.from(data.getString(KEY_RECIPIENT)),
                                            UUID.fromString(data.getString(KEY_UUID)));
    }
  }
}
