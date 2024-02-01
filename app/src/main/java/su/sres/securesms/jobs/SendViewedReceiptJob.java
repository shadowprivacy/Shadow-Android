package su.sres.securesms.jobs;
import android.app.Application;

import androidx.annotation.NonNull;

import su.sres.securesms.crypto.UnidentifiedAccessUtil;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobmanager.impl.NetworkConstraint;
import su.sres.core.util.logging.Log;
import su.sres.securesms.net.NotPushRegisteredException;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.recipients.RecipientUtil;
import su.sres.securesms.util.FeatureFlags;
import su.sres.securesms.util.TextSecurePreferences;
import su.sres.signalservice.api.SignalServiceMessageSender;
import su.sres.signalservice.api.crypto.UntrustedIdentityException;
import su.sres.signalservice.api.messages.SignalServiceReceiptMessage;
import su.sres.signalservice.api.push.SignalServiceAddress;
import su.sres.signalservice.api.push.exceptions.PushNetworkException;
import su.sres.signalservice.api.push.exceptions.ServerRejectedException;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SendViewedReceiptJob extends BaseJob {

    public static final String KEY = "SendViewedReceiptJob";

    private static final String TAG = Log.tag(SendViewedReceiptJob.class);

    private static final String KEY_THREAD          = "thread";
    private static final String KEY_ADDRESS         = "address";
    private static final String KEY_RECIPIENT       = "recipient";
    private static final String KEY_SYNC_TIMESTAMPS = "message_ids";
    private static final String KEY_TIMESTAMP       = "timestamp";

    private long        threadId;
    private RecipientId recipientId;
    private List<Long>  syncTimestamps;
    private long        timestamp;

    public SendViewedReceiptJob(long threadId, @NonNull RecipientId recipientId, long syncTimestamp) {
        this(threadId, recipientId, Collections.singletonList(syncTimestamp));
    }

    public SendViewedReceiptJob(long threadId, @NonNull RecipientId recipientId, @NonNull List<Long> syncTimestamps) {
        this(new Parameters.Builder()
                        .addConstraint(NetworkConstraint.KEY)
                        .setLifespan(TimeUnit.DAYS.toMillis(1))
                        .setMaxAttempts(Parameters.UNLIMITED)
                        .build(),
                threadId,
                recipientId,
                SendReadReceiptJob.ensureSize(syncTimestamps, SendReadReceiptJob.MAX_TIMESTAMPS),
                System.currentTimeMillis());
    }

    private SendViewedReceiptJob(@NonNull Parameters parameters,
                                 long threadId,
                                 @NonNull RecipientId recipientId,
                                 @NonNull List<Long> syncTimestamps,
                                 long timestamp)
    {
        super(parameters);

        this.threadId       = threadId;
        this.recipientId    = recipientId;
        this.syncTimestamps = syncTimestamps;
        this.timestamp      = timestamp;
    }

    @Override
    public @NonNull Data serialize() {
        return new Data.Builder().putString(KEY_RECIPIENT, recipientId.serialize())
                .putLongListAsArray(KEY_SYNC_TIMESTAMPS, syncTimestamps)
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
        if (!Recipient.self().isRegistered()) {
            throw new NotPushRegisteredException();
        }

        if (!TextSecurePreferences.isReadReceiptsEnabled(context) || syncTimestamps.isEmpty() || !FeatureFlags.sendViewedReceipts()) return;

        if (!RecipientUtil.isMessageRequestAccepted(context, threadId)) {
            Log.w(TAG, "Refusing to send receipts to untrusted recipient");
            return;
        }

        Recipient recipient = Recipient.resolved(recipientId);
        if (recipient.isBlocked()) {
            Log.w(TAG, "Refusing to send receipts to blocked recipient");
            return;
        }

        if (recipient.isGroup()) {
            Log.w(TAG, "Refusing to send receipts to group");
            return;
        }

        SignalServiceMessageSender  messageSender  = ApplicationDependencies.getSignalServiceMessageSender();
        SignalServiceAddress        remoteAddress  = RecipientUtil.toSignalServiceAddress(context, recipient);
        SignalServiceReceiptMessage receiptMessage = new SignalServiceReceiptMessage(SignalServiceReceiptMessage.Type.VIEWED,
                syncTimestamps,
                timestamp);

        messageSender.sendReceipt(remoteAddress,
                UnidentifiedAccessUtil.getAccessFor(context, Recipient.resolved(recipientId)),
                receiptMessage);
    }

    @Override
    public boolean onShouldRetry(@NonNull Exception e) {
        if (e instanceof ServerRejectedException) return false;
        if (e instanceof PushNetworkException) return true;
        return false;
    }

    @Override
    public void onFailure() {
        Log.w(TAG, "Failed to send read receipts to: " + recipientId);
    }

    public static final class Factory implements Job.Factory<SendViewedReceiptJob> {

        private final Application application;

        public Factory(@NonNull Application application) {
            this.application = application;
        }

        @Override
        public @NonNull
        SendViewedReceiptJob create(@NonNull Parameters parameters, @NonNull Data data) {
            long        timestamp      = data.getLong(KEY_TIMESTAMP);
            List<Long>  syncTimestamps = data.getLongArrayAsList(KEY_SYNC_TIMESTAMPS);
            RecipientId recipientId    = data.hasString(KEY_RECIPIENT) ? RecipientId.from(data.getString(KEY_RECIPIENT))
                    : Recipient.external(application, data.getString(KEY_ADDRESS)).getId();
            long        threadId       = data.getLong(KEY_THREAD);

            return new SendViewedReceiptJob(parameters, threadId, recipientId, syncTimestamps, timestamp);
        }
    }
}
