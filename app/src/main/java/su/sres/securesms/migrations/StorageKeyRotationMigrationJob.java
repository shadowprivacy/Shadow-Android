package su.sres.securesms.migrations;

import androidx.annotation.NonNull;

import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobmanager.JobManager;
import su.sres.securesms.jobs.MultiDeviceKeysUpdateJob;
import su.sres.securesms.jobs.MultiDeviceStorageSyncRequestJob;
import su.sres.securesms.jobs.StorageForcePushJob;
import su.sres.securesms.jobs.StorageSyncJob;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.securesms.logging.Log;
import su.sres.securesms.util.TextSecurePreferences;

public class StorageKeyRotationMigrationJob extends MigrationJob {

    private static final String TAG = Log.tag(StorageKeyRotationMigrationJob.class);

    public static final String KEY = "StorageKeyRotationMigrationJob";

    StorageKeyRotationMigrationJob() {
        this(new Parameters.Builder().build());
    }

    private StorageKeyRotationMigrationJob(@NonNull Parameters parameters) {
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
        JobManager jobManager = ApplicationDependencies.getJobManager();
        SignalStore.storageServiceValues().rotateStorageMasterKey();

        if (TextSecurePreferences.isMultiDevice(context)) {
            Log.i(TAG, "Multi-device.");
            jobManager.startChain(new StorageForcePushJob())
                    .then(new MultiDeviceKeysUpdateJob())
                    .then(new MultiDeviceStorageSyncRequestJob())
                    .enqueue();
        } else {
            Log.i(TAG, "Single-device.");
            jobManager.add(new StorageForcePushJob());
        }
    }

    @Override
    boolean shouldRetry(@NonNull Exception e) {
        return false;
    }

    public static class Factory implements Job.Factory<StorageKeyRotationMigrationJob> {
        @Override
        public @NonNull StorageKeyRotationMigrationJob create(@NonNull Parameters parameters, @NonNull Data data) {
            return new StorageKeyRotationMigrationJob(parameters);
        }
    }
}