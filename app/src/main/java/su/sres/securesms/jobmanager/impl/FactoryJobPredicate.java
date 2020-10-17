package su.sres.securesms.jobmanager.impl;

import androidx.annotation.NonNull;

import su.sres.securesms.jobmanager.JobPredicate;
import su.sres.securesms.jobmanager.persistence.JobSpec;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * A {@link JobPredicate} that will only run jobs with the provided factory keys.
 */
public final class FactoryJobPredicate implements JobPredicate {

    private final Set<String> factories;

    public FactoryJobPredicate(String... factories) {
        this.factories = new HashSet<>(Arrays.asList(factories));
    }

    @Override
    public boolean shouldRun(@NonNull JobSpec jobSpec) {
        return factories.contains(jobSpec.getFactoryKey());
    }
}