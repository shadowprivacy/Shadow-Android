package su.sres.securesms.jobmanager.migrations;

import android.content.Context;

import androidx.annotation.NonNull;

import su.sres.core.util.logging.Log;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.NoSuchMessageException;
import su.sres.securesms.database.PushDatabase;
import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.JobMigration;
import su.sres.securesms.jobs.FailingJob;
import su.sres.signalservice.api.messages.SignalServiceEnvelope;

/**
 * We removed the messageId property from the job data and replaced it with a serialized envelope,
 * so we need to take jobs that referenced an ID and replace it with the envelope instead.
 */
public class PushDecryptMessageJobEnvelopeMigration extends JobMigration {

    private static final String TAG = Log.tag(PushDecryptMessageJobEnvelopeMigration.class);

    private final PushDatabase pushDatabase;

    public PushDecryptMessageJobEnvelopeMigration(@NonNull Context context) {
        super(8);
        this.pushDatabase = DatabaseFactory.getPushDatabase(context);
    }

    @Override
    protected @NonNull JobData migrate(@NonNull JobData jobData) {
        if ("PushDecryptJob".equals(jobData.getFactoryKey())) {
            Log.i(TAG, "Found a PushDecryptJob to migrate.");
            return migratePushDecryptMessageJob(pushDatabase, jobData);
        } else {
            return jobData;
        }
    }

    private static @NonNull JobData migratePushDecryptMessageJob(@NonNull PushDatabase pushDatabase, @NonNull JobData jobData) {
        Data data = jobData.getData();

        if (data.hasLong("message_id")) {
            long messageId = data.getLong("message_id");
            try {
                SignalServiceEnvelope envelope = pushDatabase.get(messageId);
                return jobData.withData(jobData.getData()
                        .buildUpon()
                        .putBlobAsString("envelope", envelope.serialize())
                        .build());
            } catch (NoSuchMessageException e) {
                Log.w(TAG, "Failed to find envelope in DB! Failing.");
                return jobData.withFactoryKey(FailingJob.KEY);
            }
        } else {
            Log.w(TAG, "No message_id property?");
            return jobData;
        }
    }
}
