package su.sres.securesms.jobs;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;
import com.fasterxml.jackson.annotation.JsonProperty;

import su.sres.core.util.logging.Log;
import su.sres.securesms.crypto.UnidentifiedAccessUtil;
import su.sres.securesms.database.MessageDatabase.SyncMessageId;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobmanager.JobManager;
import su.sres.securesms.jobmanager.impl.NetworkConstraint;
import su.sres.securesms.net.NotPushRegisteredException;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.recipients.RecipientUtil;
import su.sres.securesms.util.JsonUtils;
import su.sres.securesms.util.TextSecurePreferences;
import su.sres.securesms.util.Util;
import su.sres.signalservice.api.SignalServiceMessageSender;
import su.sres.signalservice.api.crypto.UntrustedIdentityException;
import su.sres.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import su.sres.signalservice.api.messages.multidevice.ViewedMessage;
import su.sres.signalservice.api.push.exceptions.PushNetworkException;
import su.sres.signalservice.api.push.exceptions.ServerRejectedException;

import java.io.IOException;
import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MultiDeviceViewedUpdateJob extends BaseJob {

    public static final String KEY = "MultiDeviceViewedUpdateJob";

    private static final String TAG = Log.tag(MultiDeviceViewedUpdateJob.class);

    private static final String KEY_MESSAGE_IDS = "message_ids";

    private List<SerializableSyncMessageId> messageIds;

    private MultiDeviceViewedUpdateJob(List<SyncMessageId> messageIds) {
        this(new Parameters.Builder()
                        .addConstraint(NetworkConstraint.KEY)
                        .setLifespan(TimeUnit.DAYS.toMillis(1))
                        .setMaxAttempts(Parameters.UNLIMITED)
                        .build(),
                SendReadReceiptJob.ensureSize(messageIds, SendReadReceiptJob.MAX_TIMESTAMPS));
    }

    private MultiDeviceViewedUpdateJob(@NonNull Parameters parameters, @NonNull List<SyncMessageId> messageIds) {
        super(parameters);

        this.messageIds = new LinkedList<>();

        for (SyncMessageId messageId : messageIds) {
            this.messageIds.add(new SerializableSyncMessageId(messageId.getRecipientId().serialize(), messageId.getTimetamp()));
        }
    }

    /**
     * Enqueues all the necessary jobs for read receipts, ensuring that they're all within the
     * maximum size.
     */
    public static void enqueue(@NonNull List<SyncMessageId> messageIds) {
        JobManager                jobManager      = ApplicationDependencies.getJobManager();
        List<List<SyncMessageId>> messageIdChunks = Util.chunk(messageIds, SendReadReceiptJob.MAX_TIMESTAMPS);

        if (messageIdChunks.size() > 1) {
            Log.w(TAG, "Large receipt count! Had to break into multiple chunks. Total count: " + messageIds.size());
        }

        for (List<SyncMessageId> chunk : messageIdChunks) {
            jobManager.add(new MultiDeviceViewedUpdateJob(chunk));
        }
    }

    @Override
    public @NonNull Data serialize() {
        String[] ids = new String[messageIds.size()];

        for (int i = 0; i < ids.length; i++) {
            try {
                ids[i] = JsonUtils.toJson(messageIds.get(i));
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        }

        return new Data.Builder().putStringArray(KEY_MESSAGE_IDS, ids).build();
    }

    @Override
    public @NonNull String getFactoryKey() {
        return KEY;
    }

    @Override
    public void onRun() throws IOException, UntrustedIdentityException {
        if (!Recipient.self().isRegistered()) {
            throw new NotPushRegisteredException();
        }

        if (!TextSecurePreferences.isMultiDevice(context)) {
            Log.i(TAG, "Not multi device...");
            return;
        }

        List<ViewedMessage> viewedMessages = new LinkedList<>();

        for (SerializableSyncMessageId messageId : messageIds) {
            Recipient recipient = Recipient.resolved(RecipientId.from(messageId.recipientId));
            if (!recipient.isGroup()) {
                viewedMessages.add(new ViewedMessage(RecipientUtil.toSignalServiceAddress(context, recipient), messageId.timestamp));
            }
        }

        SignalServiceMessageSender messageSender = ApplicationDependencies.getSignalServiceMessageSender();
        messageSender.sendSyncMessage(SignalServiceSyncMessage.forViewed(viewedMessages), UnidentifiedAccessUtil.getAccessForSync(context));
    }

    @Override
    public boolean onShouldRetry(@NonNull Exception exception) {
        if (exception instanceof ServerRejectedException) return false;
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

    public static final class Factory implements Job.Factory<MultiDeviceViewedUpdateJob> {
        @Override
        public @NonNull MultiDeviceViewedUpdateJob create(@NonNull Parameters parameters, @NonNull Data data) {
            List<SyncMessageId> ids = Stream.of(data.getStringArray(KEY_MESSAGE_IDS))
                    .map(id -> {
                        try {
                            return JsonUtils.fromJson(id, SerializableSyncMessageId.class);
                        } catch (IOException e) {
                            throw new AssertionError(e);
                        }
                    })
                    .map(id -> new SyncMessageId(RecipientId.from(id.recipientId), id.timestamp))
                    .toList();

            return new MultiDeviceViewedUpdateJob(parameters, ids);
        }
    }
}
