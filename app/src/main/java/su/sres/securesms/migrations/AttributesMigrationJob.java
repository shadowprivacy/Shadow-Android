package su.sres.securesms.migrations;

import androidx.annotation.NonNull;

import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobs.ProfileUploadJob;
import su.sres.securesms.jobs.RefreshAttributesJob;
import su.sres.securesms.jobs.RefreshOwnProfileJob;
import su.sres.securesms.logging.Log;

/**
 * Schedules a re-upload of the users attributes followed by a download of their profile.
 */
public final class AttributesMigrationJob extends MigrationJob {

    private static final String TAG = Log.tag(AttributesMigrationJob.class);

    public static final String KEY = "AttributesMigrationJob";

    AttributesMigrationJob() {
        this(new Parameters.Builder().build());
    }

    private AttributesMigrationJob(@NonNull Parameters parameters) {
        super(parameters);
    }

    @Override
    public boolean isUiBlocking() {
        return false;
    }

    @Override
    public @NonNull String getFactoryKey() {
        return KEY;
    }

    @Override
    public void performMigration() {
        Log.i(TAG, "Scheduling attributes upload and profile refresh job chain");
        ApplicationDependencies.getJobManager().startChain(new RefreshAttributesJob())
                .then(new RefreshOwnProfileJob())
                .enqueue();
    }

    @Override
    boolean shouldRetry(@NonNull Exception e) {
        return false;
    }

    public static class Factory implements Job.Factory<AttributesMigrationJob> {
        @Override
        public @NonNull AttributesMigrationJob create(@NonNull Parameters parameters, @NonNull Data data) {
            return new AttributesMigrationJob(parameters);
        }
    }
}
