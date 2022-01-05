package su.sres.securesms.jobs;

import androidx.annotation.NonNull;

import com.fasterxml.jackson.annotation.JsonProperty;

import su.sres.securesms.crypto.UnidentifiedAccessUtil;
import su.sres.securesms.database.MessageDatabase.SyncMessageId;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobmanager.impl.NetworkConstraint;
import su.sres.securesms.logging.Log;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.recipients.RecipientUtil;
import su.sres.securesms.util.JsonUtils;
import su.sres.securesms.util.TextSecurePreferences;
import su.sres.signalservice.api.SignalServiceMessageSender;
import su.sres.signalservice.api.crypto.UntrustedIdentityException;
import su.sres.signalservice.api.messages.multidevice.ViewOnceOpenMessage;
import su.sres.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import su.sres.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;
import java.io.Serializable;
import java.util.concurrent.TimeUnit;

public class MultiDeviceViewOnceOpenJob extends BaseJob {

    public static final String KEY = "MultiDeviceRevealUpdateJob";

    private static final String TAG = Log.tag(MultiDeviceViewOnceOpenJob.class);

    private static final String KEY_MESSAGE_ID = "message_id";

    private SerializableSyncMessageId messageId;

    public MultiDeviceViewOnceOpenJob(SyncMessageId messageId) {
        this(new Parameters.Builder()
                        .addConstraint(NetworkConstraint.KEY)
                        .setLifespan(TimeUnit.DAYS.toMillis(1))
                        .setMaxAttempts(Parameters.UNLIMITED)
                        .build(),
                messageId);
    }

    private MultiDeviceViewOnceOpenJob(@NonNull Parameters parameters, @NonNull SyncMessageId syncMessageId) {
        super(parameters);
        this.messageId = new SerializableSyncMessageId(syncMessageId.getRecipientId().serialize(), syncMessageId.getTimetamp());
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
        Recipient                  recipient     = Recipient.resolved(RecipientId.from(messageId.recipientId));
        ViewOnceOpenMessage        openMessage   = new ViewOnceOpenMessage(RecipientUtil.toSignalServiceAddress(context, recipient), messageId.timestamp);

        messageSender.sendMessage(SignalServiceSyncMessage.forViewOnceOpen(openMessage), UnidentifiedAccessUtil.getAccessForSync(context));
    }

    @Override
    public boolean onShouldRetry(@NonNull Exception exception) {
        return exception instanceof PushNetworkException;
    }

    @Override
    public void onFailure() {

    }

    private static class SerializableSyncMessageId implements Serializable {

        private static final long serialVersionUID = 1L;

        @JsonProperty
        private final String recipientId;

        @JsonProperty
        private final long   timestamp;

        private SerializableSyncMessageId(@JsonProperty("recipientId") String recipientId, @JsonProperty("timestamp") long timestamp) {
            this.recipientId = recipientId;
            this.timestamp   = timestamp;
        }
    }

    public static final class Factory implements Job.Factory<MultiDeviceViewOnceOpenJob> {
        @Override
        public @NonNull MultiDeviceViewOnceOpenJob create(@NonNull Parameters parameters, @NonNull Data data) {
            SerializableSyncMessageId messageId;

            try {
                messageId = JsonUtils.fromJson(data.getString(KEY_MESSAGE_ID), SerializableSyncMessageId.class);
            } catch (IOException e) {
                throw new AssertionError(e);
            }

            SyncMessageId syncMessageId = new SyncMessageId(RecipientId.from(messageId.recipientId), messageId.timestamp);

            return new MultiDeviceViewOnceOpenJob(parameters, syncMessageId);
        }
    }
}