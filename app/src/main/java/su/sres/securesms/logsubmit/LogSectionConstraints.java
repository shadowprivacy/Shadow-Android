package su.sres.securesms.logsubmit;

import android.content.Context;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobmanager.Constraint;
import su.sres.securesms.jobs.JobManagerFactories;
import su.sres.securesms.util.Util;

import java.util.Map;

final class LogSectionConstraints implements LogSection {

    @Override
    public @NonNull String getTitle() {
        return "CONSTRAINTS";
    }

    @Override
    public @NonNull CharSequence getContent(@NonNull Context context) {
        StringBuilder                   output    = new StringBuilder();
        Map<String, Constraint.Factory> factories = JobManagerFactories.getConstraintFactories(ApplicationDependencies.getApplication());
        int                             keyLength = Stream.of(factories.keySet()).map(String::length).max(Integer::compareTo).orElse(0);

        for (Map.Entry<String, Constraint.Factory> entry : factories.entrySet()) {
            output.append(Util.rightPad(entry.getKey(), keyLength)).append(": ").append(entry.getValue().create().isMet()).append("\n");
        }

        return output;
    }
}
