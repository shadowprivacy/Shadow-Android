package su.sres.securesms.jobs;

import androidx.annotation.NonNull;

import su.sres.core.util.logging.Log;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;

/**
 * A job that has the same queue as {@link PushDecryptMessageJob} that we enqueue so we can notify
 * the {@link su.sres.securesms.messages.IncomingMessageObserver} when decryptions have
 * finished. This lets us know not just when the websocket is drained, but when all the decryptions
 * for the messages we pulled down from the websocket have been finished.
 */
public class PushDecryptDrainedJob extends BaseJob {

    public static final String KEY = "PushDecryptDrainedJob";

    private static final String TAG = Log.tag(PushDecryptDrainedJob.class);

    public PushDecryptDrainedJob() {
        this(new Parameters.Builder()
                .setQueue(PushDecryptMessageJob.QUEUE)
                .build());
    }

    private PushDecryptDrainedJob(@NonNull Parameters parameters) {
        super(parameters);
    }

    @Override
    public @NonNull Data serialize() {
        return Data.EMPTY;
    }

    @Override
    protected void onRun() throws Exception {
        Log.i(TAG, "Decryptions are caught-up.");
        ApplicationDependencies.getIncomingMessageObserver().notifyDecryptionsDrained();
    }

    @Override
    protected boolean onShouldRetry(@NonNull Exception e) {
        return false;
    }

    @Override
    public @NonNull String getFactoryKey() {
        return KEY;
    }

    @Override
    public void onFailure() {
    }

    public static final class Factory implements Job.Factory<PushDecryptDrainedJob> {
        @Override
        public @NonNull PushDecryptDrainedJob create(@NonNull Parameters parameters, @NonNull Data data) {
            return new PushDecryptDrainedJob(parameters);
        }
    }
}
