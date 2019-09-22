package su.sres.securesms.jobs;

import androidx.annotation.NonNull;

import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobmanager.JobLogger;
import su.sres.securesms.logging.Log;

public abstract class BaseJob extends Job {

    private static final String TAG = BaseJob.class.getSimpleName();

    public BaseJob(@NonNull Parameters parameters) {
        super(parameters);
    }

    @Override
    public @NonNull Result run() {
        try {
            onRun();
            return Result.SUCCESS;
        } catch (Exception e) {
            if (onShouldRetry(e)) {
                Log.i(TAG, JobLogger.format(this, "Encountered a retryable exception."), e);
                return Result.RETRY;
            } else {
                Log.w(TAG, JobLogger.format(this, "Encountered a failing exception."), e);
                return Result.FAILURE;
            }
        }
    }

    protected abstract void onRun() throws Exception;

    protected abstract boolean onShouldRetry(@NonNull Exception e);
}