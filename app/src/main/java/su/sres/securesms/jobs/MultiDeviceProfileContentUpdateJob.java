package su.sres.securesms.jobs;

import androidx.annotation.NonNull;

import su.sres.securesms.crypto.UnidentifiedAccessUtil;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobmanager.impl.NetworkConstraint;
import su.sres.securesms.logging.Log;
import su.sres.securesms.util.TextSecurePreferences;
import su.sres.signalservice.api.SignalServiceMessageSender;
import su.sres.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import su.sres.signalservice.api.push.exceptions.PushNetworkException;

public class MultiDeviceProfileContentUpdateJob extends BaseJob {

    public static final String KEY = "MultiDeviceProfileContentUpdateJob";

    private static final String TAG = Log.tag(MultiDeviceProfileContentUpdateJob.class);

    public MultiDeviceProfileContentUpdateJob() {
        this(new Parameters.Builder()
                .setQueue("MultiDeviceProfileUpdateJob")
                .setMaxInstances(2)
                .addConstraint(NetworkConstraint.KEY)
                .setMaxAttempts(10)
                .build());
    }

    private MultiDeviceProfileContentUpdateJob(@NonNull Parameters parameters) {
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

        messageSender.sendMessage(SignalServiceSyncMessage.forFetchLatest(SignalServiceSyncMessage.FetchType.LOCAL_PROFILE),
                UnidentifiedAccessUtil.getAccessForSync(context));
    }

    @Override
    protected boolean onShouldRetry(@NonNull Exception e) {
        return e instanceof PushNetworkException;
    }


    @Override
    public void onCanceled() {
        Log.w(TAG, "Did not succeed!");
    }

    public static final class Factory implements Job.Factory<MultiDeviceProfileContentUpdateJob> {
        @Override
        public @NonNull MultiDeviceProfileContentUpdateJob create(@NonNull Parameters parameters, @NonNull Data data) {
            return new MultiDeviceProfileContentUpdateJob(parameters);
        }
    }
}