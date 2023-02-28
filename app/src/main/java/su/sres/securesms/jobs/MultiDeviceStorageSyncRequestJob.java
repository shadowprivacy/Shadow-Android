package su.sres.securesms.jobs;

import androidx.annotation.NonNull;

import su.sres.securesms.crypto.UnidentifiedAccessUtil;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobmanager.impl.NetworkConstraint;
import su.sres.core.util.logging.Log;
import su.sres.securesms.util.TextSecurePreferences;
import su.sres.signalservice.api.SignalServiceMessageSender;
import su.sres.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import su.sres.signalservice.api.push.exceptions.PushNetworkException;

public class MultiDeviceStorageSyncRequestJob extends BaseJob {

    public static final String KEY = "MultiDeviceStorageSyncRequestJob";

    private static final String TAG = Log.tag(MultiDeviceStorageSyncRequestJob.class);

    public MultiDeviceStorageSyncRequestJob() {
        this(new Parameters.Builder()
                .setQueue("MultiDeviceStorageSyncRequestJob")
                .setMaxInstancesForFactory(2)
                .addConstraint(NetworkConstraint.KEY)
                .setMaxAttempts(10)
                .build());
    }

    private MultiDeviceStorageSyncRequestJob(@NonNull Parameters parameters) {
        super(parameters);
    }

    @Override
    public @NonNull Data serialize() {
        return Data.EMPTY;
    }

    @Override
    public @NonNull String getFactoryKey() {
        return KEY;
    }

    @Override
    protected void onRun() throws Exception {
        if (!TextSecurePreferences.isMultiDevice(context)) {
            Log.i(TAG, "Not multi device, aborting...");
            return;
        }

        SignalServiceMessageSender messageSender = ApplicationDependencies.getSignalServiceMessageSender();

        messageSender.sendMessage(SignalServiceSyncMessage.forFetchLatest(SignalServiceSyncMessage.FetchType.STORAGE_MANIFEST),
                UnidentifiedAccessUtil.getAccessForSync(context));
    }

    @Override
    protected boolean onShouldRetry(@NonNull Exception e) {
        return e instanceof PushNetworkException;
    }

    @Override
    public void onFailure() {
        Log.w(TAG, "Did not succeed!");
    }

    public static final class Factory implements Job.Factory<MultiDeviceStorageSyncRequestJob> {
        @Override
        public @NonNull MultiDeviceStorageSyncRequestJob create(@NonNull Parameters parameters, @NonNull Data data) {
            return new MultiDeviceStorageSyncRequestJob(parameters);
        }
    }
}