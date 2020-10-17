package su.sres.securesms.jobmanager;

import androidx.annotation.NonNull;

import su.sres.securesms.jobmanager.persistence.JobSpec;

public interface JobPredicate {
    JobPredicate NONE = jobSpec -> true;

    boolean shouldRun(@NonNull JobSpec jobSpec);
}