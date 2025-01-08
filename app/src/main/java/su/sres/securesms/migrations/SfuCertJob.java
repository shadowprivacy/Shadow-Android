package su.sres.securesms.migrations;

import androidx.annotation.NonNull;

import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobs.CertificatePullJob;
import su.sres.core.util.logging.Log;
import su.sres.securesms.keyvalue.SignalStore;

/**
 * Triggers a certificate pull to import the SFU certificate into the truststore.
 */
public final class SfuCertJob extends MigrationJob {

    private static final String TAG = Log.tag(SfuCertJob.class);

    public static final String KEY = "SfuCertJob";

    SfuCertJob() {
        this(new Parameters.Builder().build());
    }

    private SfuCertJob(@NonNull Parameters parameters) {
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

        // launch CertificatePullJob
        // should not be launched if the client is not (yet) registered, which would be the case e.g. on first-ever install
        if (SignalStore.account().isRegistered()) {
            Log.i(TAG, "Scheduling certificate pull job");
            ApplicationDependencies.getJobManager().add(new CertificatePullJob());
        }
    }

    @Override
    boolean shouldRetry(@NonNull Exception e) {
        return false;
    }

    public static class Factory implements Job.Factory<SfuCertJob> {
        @Override
        public @NonNull
        SfuCertJob create(@NonNull Parameters parameters, @NonNull Data data) {
            return new SfuCertJob(parameters);
        }
    }
}

