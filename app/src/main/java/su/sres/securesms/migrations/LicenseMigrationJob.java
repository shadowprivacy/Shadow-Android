package su.sres.securesms.migrations;

import androidx.annotation.NonNull;

import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobs.LicenseManagementJob;
import su.sres.securesms.keyvalue.MiscellaneousValues;
import su.sres.securesms.keyvalue.ServiceConfigurationValues;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.core.util.logging.Log;
import su.sres.securesms.util.TextSecurePreferences;

/**
 * Performs cleanup of keyvalues related to the old activation scheme, and substitutes server-side activation key for the old client-side key.
 */
public final class LicenseMigrationJob extends MigrationJob {

    private static final String TAG = Log.tag(LicenseMigrationJob.class);

    public static final String KEY = "LicenseMigrationJob";

    private final ServiceConfigurationValues config = SignalStore.serviceConfigurationValues();
    private final MiscellaneousValues misc = SignalStore.misc();

    LicenseMigrationJob() {
        this(new Parameters.Builder().build());
    }

    private LicenseMigrationJob(@NonNull Parameters parameters) {
        super(parameters);
    }

    @Override
    public boolean isUiBlocking() {
        return true;
    }

    @Override
    public @NonNull
    String getFactoryKey() {
        return KEY;
    }

    @Override
    public void performMigration() {

        // remove obsolete keyvalues
        config.removeTrials();

        // remove old client-side license
        config.removeLicense();

        // reset
        config.setLicensed(false);
        misc.setLastLicenseRefreshTime(0);

        // launch the novel LicenseManagementJob
        // should not be launched if the client is not (yet) registered, which would be the case e.g. on first-ever install
        if (TextSecurePreferences.isPushRegistered(context)) {
            Log.i(TAG, "Scheduling license migration job");
            ApplicationDependencies.getJobManager().add(new LicenseManagementJob());
        }
    }

    @Override
    boolean shouldRetry(@NonNull Exception e) {
        return false;
    }

    public static class Factory implements Job.Factory<LicenseMigrationJob> {
        @Override
        public @NonNull
        LicenseMigrationJob create(@NonNull Parameters parameters, @NonNull Data data) {
            return new LicenseMigrationJob(parameters);
        }
    }
}
