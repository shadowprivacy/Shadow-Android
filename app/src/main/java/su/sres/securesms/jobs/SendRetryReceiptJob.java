package su.sres.securesms.jobs;

import androidx.annotation.NonNull;

import su.sres.core.util.logging.Log;
import su.sres.securesms.crypto.UnidentifiedAccessUtil;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.groups.GroupId;
import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobmanager.impl.NetworkConstraint;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.recipients.RecipientUtil;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.protocol.DecryptionErrorMessage;
import org.whispersystems.libsignal.util.guava.Optional;
import su.sres.signalservice.api.crypto.UnidentifiedAccessPair;
import su.sres.signalservice.api.push.SignalServiceAddress;
import su.sres.signalservice.api.push.exceptions.PushNetworkException;

import java.util.concurrent.TimeUnit;

public final class SendRetryReceiptJob extends BaseJob {

  private static final String TAG = Log.tag(SendRetryReceiptJob.class);

  public static final String KEY = "SendRetryReceiptJob";

  private static final String KEY_RECIPIENT_ID  = "recipient_id";
  private static final String KEY_ERROR_MESSAGE = "error_message";
  private static final String KEY_GROUP_ID      = "group_id";

  private final RecipientId            recipientId;
  private final Optional<GroupId>      groupId;
  private final DecryptionErrorMessage errorMessage;

  public SendRetryReceiptJob(@NonNull RecipientId recipientId, @NonNull Optional<GroupId> groupId, @NonNull DecryptionErrorMessage errorMessage) {
    this(recipientId,
         groupId,
         errorMessage,
         new Parameters.Builder()
             .addConstraint(NetworkConstraint.KEY)
             .setQueue(recipientId.toQueueKey())
             .setMaxAttempts(Parameters.UNLIMITED)
             .setLifespan(TimeUnit.DAYS.toMillis(1))
             .build());
  }

  private SendRetryReceiptJob(@NonNull RecipientId recipientId,
                              @NonNull Optional<GroupId> groupId,
                              @NonNull DecryptionErrorMessage errorMessage,
                              @NonNull Parameters parameters)
  {
    super(parameters);
    this.recipientId  = recipientId;
    this.groupId      = groupId;
    this.errorMessage = errorMessage;
  }

  @Override
  public @NonNull Data serialize() {
    Data.Builder builder = new Data.Builder()
        .putString(KEY_RECIPIENT_ID, recipientId.serialize())
        .putBlobAsString(KEY_ERROR_MESSAGE, errorMessage.serialize());

    if (groupId.isPresent()) {
      builder.putBlobAsString(KEY_GROUP_ID, groupId.get().getDecodedId());
    }

    return builder.build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  protected void onRun() throws Exception {
    Recipient recipient = Recipient.resolved(recipientId);

    if (recipient.isUnregistered()) {
      Log.w(TAG, recipient.getId() + " not registered!");
      return;
    }

    SignalServiceAddress             address   = RecipientUtil.toSignalServiceAddress(context, recipient);
    Optional<UnidentifiedAccessPair> access    = UnidentifiedAccessUtil.getAccessFor(context, recipient);
    Optional<byte[]>                 group     = groupId.transform(GroupId::getDecodedId);

    Log.i(TAG, "Sending retry receipt for " + errorMessage.getTimestamp() + " to " + recipientId + ", device: " + errorMessage.getDeviceId());
    ApplicationDependencies.getSignalServiceMessageSender().sendRetryReceipt(address, access, group, errorMessage);
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof PushNetworkException;
  }

  @Override
  public void onFailure() {
  }

  public static final class Factory implements Job.Factory<SendRetryReceiptJob> {
    @Override
    public @NonNull SendRetryReceiptJob create(@NonNull Parameters parameters, @NonNull Data data) {
      try {
        RecipientId            recipientId  = RecipientId.from(data.getString(KEY_RECIPIENT_ID));
        DecryptionErrorMessage errorMessage = new DecryptionErrorMessage(data.getStringAsBlob(KEY_ERROR_MESSAGE));
        Optional<GroupId>      groupId      = Optional.absent();

        if (data.hasString(KEY_GROUP_ID)) {
          groupId = Optional.of(GroupId.pushOrThrow(data.getStringAsBlob(KEY_GROUP_ID)));
        }

        return new SendRetryReceiptJob(recipientId, groupId, errorMessage, parameters);
      } catch (InvalidMessageException e) {
        throw new AssertionError(e);
      }
    }
  }
}
