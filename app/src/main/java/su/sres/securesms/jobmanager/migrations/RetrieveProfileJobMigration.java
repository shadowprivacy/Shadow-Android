package su.sres.securesms.jobmanager.migrations;

import androidx.annotation.NonNull;

import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.JobMigration;
import su.sres.core.util.logging.Log;

public class RetrieveProfileJobMigration extends JobMigration {

    private static final String TAG = Log.tag(RetrieveProfileJobMigration.class);

    public RetrieveProfileJobMigration() {
        super(7);
    }

    @Override
    protected @NonNull JobData migrate(@NonNull JobData jobData) {
        Log.i(TAG, "Running.");

        if ("RetrieveProfileJob".equals(jobData.getFactoryKey())) {
            return migrateRetrieveProfileJob(jobData);
        }
        return jobData;
    }

    private static @NonNull JobData migrateRetrieveProfileJob(@NonNull JobData jobData) {
        Data data = jobData.getData();

        if (data.hasString("recipient")) {
            Log.i(TAG, "Migrating job.");

            String recipient = data.getString("recipient");
            return jobData.withData(new Data.Builder()
                    .putStringArray("recipients", new String[] { recipient })
                    .build());
        } else {
            return jobData;
        }
    }

}