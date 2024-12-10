package su.sres.securesms.jobs;

import androidx.annotation.NonNull;

import su.sres.core.util.logging.Log;

import java.util.concurrent.TimeUnit;

import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobmanager.impl.NetworkConstraint;
import su.sres.securesms.util.ProfileUtil;
import su.sres.securesms.util.TextSecurePreferences;

public final class ProfileUploadJob extends BaseJob {

    private static final String TAG = Log.tag(ProfileUploadJob.class);

    public static final String KEY = "ProfileUploadJob";

    public static final String QUEUE = "ProfileAlteration";

    public ProfileUploadJob() {
        this(new Job.Parameters.Builder()
                .addConstraint(NetworkConstraint.KEY)
                .setQueue(QUEUE)
                .setLifespan(TimeUnit.DAYS.toMillis(30))
                .setMaxAttempts(Parameters.UNLIMITED)
                .setMaxInstancesForFactory(2)
                .build());
    }

    private ProfileUploadJob(@NonNull Parameters parameters) {
        super(parameters);
    }

    @Override
    protected void onRun() throws Exception {
        if (!TextSecurePreferences.isPushRegistered(context)) {
            Log.w(TAG, "Not registered. Skipping.");
            return;
        }

        ProfileUtil.uploadProfile(context);
        Log.i(TAG, "Profile uploaded.");
    }

    @Override
    protected boolean onShouldRetry(@NonNull Exception e) {
        return true;
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
    public void onFailure() {
    }

    public static class Factory implements Job.Factory<ProfileUploadJob> {

        @Override
        public @NonNull ProfileUploadJob create(@NonNull Parameters parameters, @NonNull Data data) {
            return new ProfileUploadJob(parameters);
        }
    }
}