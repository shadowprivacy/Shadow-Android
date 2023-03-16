package su.sres.securesms.jobs;

import androidx.annotation.NonNull;

import su.sres.core.util.logging.Log;
import su.sres.securesms.crypto.SessionUtil;
import su.sres.securesms.crypto.UnidentifiedAccessUtil;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.MessageDatabase;
import su.sres.securesms.database.model.databaseprotos.DeviceLastResetTime;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobmanager.impl.DecryptionsDrainedConstraint;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.recipients.RecipientUtil;
import su.sres.securesms.transport.RetryLaterException;
import org.whispersystems.libsignal.util.guava.Optional;

import su.sres.securesms.util.FeatureFlags;
import su.sres.signalservice.api.SignalServiceMessageSender;
import su.sres.signalservice.api.crypto.UnidentifiedAccessPair;
import su.sres.signalservice.api.crypto.UntrustedIdentityException;
import su.sres.signalservice.api.push.SignalServiceAddress;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * - Archives the session associated with the specified device
 * - Inserts an error message in the conversation
 * - Sends a new, empty message to trigger a fresh session with the specified device
 *
 * This will only be run when all decryptions have finished, and there can only be one enqueued
 * per websocket drain cycle.
 */
public class AutomaticSessionResetJob extends BaseJob {

    private static final String TAG = Log.tag(AutomaticSessionResetJob.class);

    public static final String KEY = "AutomaticSessionResetJob";

    private static final String KEY_RECIPIENT_ID   =  "recipient_id";
    private static final String KEY_DEVICE_ID      =  "device_id";
    private static final String KEY_SENT_TIMESTAMP =  "sent_timestamp";

    private final RecipientId recipientId;
    private final int         deviceId;
    private final long        sentTimestamp;

    public AutomaticSessionResetJob(@NonNull RecipientId recipientId, int deviceId, long sentTimestamp) {
        this(new Parameters.Builder()
                        .setQueue(PushProcessMessageJob.getQueueName(recipientId))
                        .addConstraint(DecryptionsDrainedConstraint.KEY)
                        .setMaxInstancesForQueue(1)
                        .build(),
                recipientId,
                deviceId,
                sentTimestamp);
    }

    private AutomaticSessionResetJob(@NonNull Parameters parameters,
                                     @NonNull RecipientId recipientId,
                                     int deviceId,
                                     long sentTimestamp)
    {
        super(parameters);
        this.recipientId   = recipientId;
        this.deviceId      = deviceId;
        this.sentTimestamp = sentTimestamp;
    }

    @Override
    public @NonNull Data serialize() {
        return new Data.Builder().putString(KEY_RECIPIENT_ID, recipientId.serialize())
                .putInt(KEY_DEVICE_ID, deviceId)
                .putLong(KEY_SENT_TIMESTAMP, sentTimestamp)
                .build();
    }

    @Override
    public @NonNull String getFactoryKey() {
        return KEY;
    }

    @Override
    protected void onRun() throws Exception {
        SessionUtil.archiveSession(context, recipientId, deviceId);
        insertLocalMessage();
        if (FeatureFlags.automaticSessionReset()) {
            long                resetInterval      = TimeUnit.SECONDS.toMillis(FeatureFlags.automaticSessionResetIntervalSeconds());
            DeviceLastResetTime resetTimes         = DatabaseFactory.getRecipientDatabase(context).getLastSessionResetTimes(recipientId);
            long                timeSinceLastReset = System.currentTimeMillis() - getLastResetTime(resetTimes, deviceId);

            Log.i(TAG, "DeviceId: " + deviceId + ", Reset interval: " + resetInterval + ", Time since last reset: " + timeSinceLastReset);

            if (timeSinceLastReset > resetInterval) {
                Log.i(TAG, "We're good! Sending a null message.");

                DatabaseFactory.getRecipientDatabase(context).setLastSessionResetTime(recipientId, setLastResetTime(resetTimes, deviceId, System.currentTimeMillis()));
                Log.i(TAG, "Marked last reset time: " + System.currentTimeMillis());

                sendNullMessage();
                Log.i(TAG, "Successfully sent!");
            } else {
                Log.w(TAG, "Too soon! Time since last reset: " + timeSinceLastReset);
            }
        } else {
            Log.w(TAG, "Automatic session reset send disabled!");
        }
    }

    @Override
    protected boolean onShouldRetry(@NonNull Exception e) {
        return false;
    }

    @Override
    public void onFailure() {
    }

    private void insertLocalMessage() {
        MessageDatabase.InsertResult result = DatabaseFactory.getSmsDatabase(context).insertDecryptionFailedMessage(recipientId, deviceId, sentTimestamp);
        ApplicationDependencies.getMessageNotifier().updateNotification(context, result.getThreadId());
    }

    private void sendNullMessage() throws IOException {
        Recipient                        recipient          = Recipient.resolved(recipientId);
        SignalServiceMessageSender       messageSender      = ApplicationDependencies.getSignalServiceMessageSender();
        SignalServiceAddress             address            = RecipientUtil.toSignalServiceAddress(context, recipient);
        Optional<UnidentifiedAccessPair> unidentifiedAccess = UnidentifiedAccessUtil.getAccessFor(context, recipient);

        try {
            messageSender.sendNullMessage(address, unidentifiedAccess);
        } catch (UntrustedIdentityException e) {
            Log.w(TAG, "Unable to send null message.");
        }
    }

    private long getLastResetTime(@NonNull DeviceLastResetTime resetTimes, int deviceId) {
        for (DeviceLastResetTime.Pair pair : resetTimes.getResetTimeList()) {
            if (pair.getDeviceId() == deviceId) {
                return pair.getLastResetTime();
            }
        }
        return 0;
    }

    private @NonNull DeviceLastResetTime setLastResetTime(@NonNull DeviceLastResetTime resetTimes, int deviceId, long time) {
        DeviceLastResetTime.Builder builder = DeviceLastResetTime.newBuilder();

        for (DeviceLastResetTime.Pair pair : resetTimes.getResetTimeList()) {
            if (pair.getDeviceId() != deviceId) {
                builder.addResetTime(pair);
            }
        }

        builder.addResetTime(DeviceLastResetTime.Pair.newBuilder().setDeviceId(deviceId).setLastResetTime(time));

        return builder.build();
    }

    public static final class Factory implements Job.Factory<AutomaticSessionResetJob> {
        @Override
        public @NonNull AutomaticSessionResetJob create(@NonNull Parameters parameters, @NonNull Data data) {
            return new AutomaticSessionResetJob(parameters,
                    RecipientId.from(data.getString(KEY_RECIPIENT_ID)),
                    data.getInt(KEY_DEVICE_ID),
                    data.getLong(KEY_SENT_TIMESTAMP));
        }
    }
}
