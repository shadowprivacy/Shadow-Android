package su.sres.securesms.jobs;

import androidx.annotation.NonNull;

import su.sres.core.util.logging.Log;
import su.sres.securesms.crypto.UnidentifiedAccessUtil;
import su.sres.securesms.database.GroupDatabase;
import su.sres.securesms.database.ShadowDatabase;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.groups.GroupId;
import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobmanager.impl.NetworkConstraint;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.recipients.RecipientUtil;

import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.protocol.SenderKeyDistributionMessage;
import org.whispersystems.libsignal.util.guava.Optional;
import su.sres.signalservice.api.SignalServiceMessageSender;
import su.sres.signalservice.api.crypto.UnidentifiedAccessPair;
import su.sres.signalservice.api.messages.SendMessageResult;
import su.sres.signalservice.api.push.DistributionId;
import su.sres.signalservice.api.push.SignalServiceAddress;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Sends a {@link SenderKeyDistributionMessage} to a target recipient.
 * <p>
 * Will re-check group membership at send time and send the proper distribution message if they're still a member.
 */
public final class SenderKeyDistributionSendJob extends BaseJob {

  private static final String TAG = Log.tag(SenderKeyDistributionSendJob.class);

  public static final String KEY = "SenderKeyDistributionSendJob";

  private static final String KEY_RECIPIENT_ID = "recipient_id";
  private static final String KEY_GROUP_ID     = "group_id";

  private final RecipientId recipientId;
  private final GroupId.V2  groupId;

  public SenderKeyDistributionSendJob(@NonNull RecipientId recipientId, @NonNull GroupId.V2 groupId) {
    this(recipientId, groupId, new Parameters.Builder()
        .setQueue(recipientId.toQueueKey())
        .addConstraint(NetworkConstraint.KEY)
        .setLifespan(TimeUnit.DAYS.toMillis(1))
        .setMaxAttempts(Parameters.UNLIMITED)
        .setMaxInstancesForQueue(1)
        .build());
  }

  private SenderKeyDistributionSendJob(@NonNull RecipientId recipientId, @NonNull GroupId.V2 groupId, @NonNull Parameters parameters) {
    super(parameters);

    this.recipientId = recipientId;
    this.groupId     = groupId;
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder().putString(KEY_RECIPIENT_ID, recipientId.serialize())
                             .putBlobAsString(KEY_GROUP_ID, groupId.getDecodedId())
                             .build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  protected void onRun() throws Exception {
    GroupDatabase groupDatabase = ShadowDatabase.groups();

    if (!groupDatabase.isCurrentMember(groupId, recipientId)) {
      Log.w(TAG, recipientId + " is no longer a member of " + groupId + "! Not sending.");
      return;
    }

    Recipient recipient = Recipient.resolved(recipientId);

    if (recipient.getSenderKeyCapability() != Recipient.Capability.SUPPORTED) {
      Log.w(TAG, recipientId + " does not support sender key! Not sending.");
      return;
    }

    if (recipient.isUnregistered()) {
      Log.w(TAG, recipient.getId() + " not registered!");
      return;
    }

    SignalServiceMessageSender             messageSender  = ApplicationDependencies.getSignalServiceMessageSender();
    List<SignalServiceAddress>             address        = Collections.singletonList(RecipientUtil.toSignalServiceAddress(context, recipient));
    DistributionId                         distributionId = groupDatabase.getOrCreateDistributionId(groupId);
    SenderKeyDistributionMessage           message        = messageSender.getOrCreateNewGroupSession(distributionId);
    List<Optional<UnidentifiedAccessPair>> access         = UnidentifiedAccessUtil.getAccessFor(context, Collections.singletonList(recipient));

    SendMessageResult result = messageSender.sendSenderKeyDistributionMessage(distributionId, address, access, message, groupId.getDecodedId()).get(0);

    if (result.isSuccess()) {
      List<SignalProtocolAddress> addresses = result.getSuccess()
                                                    .getDevices()
                                                    .stream()
                                                    .map(device -> new SignalProtocolAddress(recipient.requireServiceId(), device))
                                                    .collect(Collectors.toList());

      ApplicationDependencies.getSenderKeyStore().markSenderKeySharedWith(distributionId, addresses);
    }
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return false;
  }

  @Override
  public void onFailure() {

  }

  public static final class Factory implements Job.Factory<SenderKeyDistributionSendJob> {

    @Override
    public @NonNull SenderKeyDistributionSendJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new SenderKeyDistributionSendJob(RecipientId.from(data.getString(KEY_RECIPIENT_ID)),
                                              GroupId.pushOrThrow(data.getStringAsBlob(KEY_GROUP_ID)).requireV2(),
                                              parameters);
    }
  }
}
