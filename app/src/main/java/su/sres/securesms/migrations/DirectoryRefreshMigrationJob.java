package su.sres.securesms.migrations;

import androidx.annotation.NonNull;

import su.sres.securesms.contacts.sync.DirectoryHelper;
import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.securesms.logging.Log;
import su.sres.securesms.util.TextSecurePreferences;

import java.io.IOException;

/**
 * Does a full directory refresh.
 */
public final class DirectoryRefreshMigrationJob extends MigrationJob {

    private static final String TAG = Log.tag(DirectoryRefreshMigrationJob.class);

    public static final String KEY = "DirectoryRefreshMigrationJob";

    DirectoryRefreshMigrationJob() {
        this(new Parameters.Builder().build());
    }

    private DirectoryRefreshMigrationJob(@NonNull Parameters parameters) {
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
    public void performMigration() throws IOException {
        if (!TextSecurePreferences.isPushRegistered(context)           ||
                !SignalStore.registrationValues().isRegistrationComplete() ||
                TextSecurePreferences.getLocalUuid(context) == null)
        {
            Log.w(TAG, "Not registered! Skipping.");
            return;
        }

        DirectoryHelper.refreshDirectory(context);
    }

    @Override
    boolean shouldRetry(@NonNull Exception e) {
        return e instanceof IOException;
    }

    public static class Factory implements Job.Factory<DirectoryRefreshMigrationJob> {
        @Override
        public @NonNull DirectoryRefreshMigrationJob create(@NonNull Parameters parameters, @NonNull Data data) {
            return new DirectoryRefreshMigrationJob(parameters);
        }
    }
}
