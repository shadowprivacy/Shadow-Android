package su.sres.securesms.jobs;

import androidx.annotation.NonNull;

import com.fasterxml.jackson.annotation.JsonProperty;

import su.sres.securesms.crypto.UnidentifiedAccessUtil;
import su.sres.securesms.database.Address;
import su.sres.securesms.database.MessagingDatabase.SyncMessageId;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobmanager.impl.NetworkConstraint;
import su.sres.securesms.logging.Log;
import su.sres.securesms.util.JsonUtils;
import su.sres.securesms.util.TextSecurePreferences;
import su.sres.signalservice.api.SignalServiceMessageSender;
import su.sres.signalservice.api.crypto.UntrustedIdentityException;
import su.sres.signalservice.api.messages.multidevice.MessageTimerReadMessage;
import su.sres.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import su.sres.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;
import java.io.Serializable;
import java.util.concurrent.TimeUnit;

public class MultiDeviceRevealUpdateJob extends BaseJob {

    public static final String KEY = "MultiDeviceRevealUpdateJob";

    private static final String TAG = MultiDeviceRevealUpdateJob.class.getSimpleName();

    private static final String KEY_MESSAGE_ID = "message_id";

    private SerializableSyncMessageId messageId;

    public MultiDeviceRevealUpdateJob(SyncMessageId messageId) {
        this(new Parameters.Builder()
                        .addConstraint(NetworkConstraint.KEY)
                        .setLifespan(TimeUnit.DAYS.toMillis(1))
                        .setMaxAttempts(Parameters.UNLIMITED)
                        .build(),
                messageId);
    }

    private MultiDeviceRevealUpdateJob(@NonNull Parameters parameters, @NonNull SyncMessageId syncMessageId) {
        super(parameters);
        this.messageId = new SerializableSyncMessageId(syncMessageId.getAddress().toPhoneString(), syncMessageId.getTimetamp());
    }

    @Override
    public @NonNull Data serialize() {
        String serialized;

        try {
            serialized = JsonUtils.toJson(messageId);
        } catch (IOException e) {
            throw new AssertionError(e);
        }

        return new Data.Builder().putString(KEY_MESSAGE_ID, serialized).build();
    }

    @Override
    public @NonNull String getFactoryKey() {
        return KEY;
    }

    @Override
    public void onRun() throws IOException, UntrustedIdentityException {
        if (!TextSecurePreferences.isMultiDevice(context)) {
            Log.i(TAG, "Not multi device...");
            return;
        }

        SignalServiceMessageSender messageSender = ApplicationDependencies.getSignalServiceMessageSender();
        MessageTimerReadMessage    timerMessage  = new MessageTimerReadMessage(messageId.sender, messageId.timestamp);

        messageSender.sendMessage(SignalServiceSyncMessage.forMessageTimerRead(timerMessage), UnidentifiedAccessUtil.getAccessForSync(context));
    }

    @Override
    public boolean onShouldRetry(@NonNull Exception exception) {
        return exception instanceof PushNetworkException;
    }

    @Override
    public void onCanceled() {

    }

    private static class SerializableSyncMessageId implements Serializable {

        private static final long serialVersionUID = 1L;

        @JsonProperty
        private final String sender;

        @JsonProperty
        private final long   timestamp;

        private SerializableSyncMessageId(@JsonProperty("sender") String sender, @JsonProperty("timestamp") long timestamp) {
            this.sender = sender;
            this.timestamp = timestamp;
        }
    }

    public static final class Factory implements Job.Factory<MultiDeviceRevealUpdateJob> {
        @Override
        public @NonNull MultiDeviceRevealUpdateJob create(@NonNull Parameters parameters, @NonNull Data data) {
            SerializableSyncMessageId messageId;

            try {
                messageId = JsonUtils.fromJson(data.getString(KEY_MESSAGE_ID), SerializableSyncMessageId.class);
            } catch (IOException e) {
                throw new AssertionError(e);
            }

            SyncMessageId syncMessageId = new SyncMessageId(Address.fromSerialized(messageId.sender), messageId.timestamp);

            return new MultiDeviceRevealUpdateJob(parameters, syncMessageId);
        }
    }
}