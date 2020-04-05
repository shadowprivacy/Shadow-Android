package su.sres.securesms.logsubmit;

import android.content.Context;

import androidx.annotation.NonNull;

import su.sres.securesms.dependencies.ApplicationDependencies;

import java.util.List;

public class LogSectionJobs implements LogSection {

    @Override
    public @NonNull String getTitle() {
        return "JOBS";
    }

    @Override
    public @NonNull CharSequence getContent(@NonNull Context context) {
        return ApplicationDependencies.getJobManager().getDebugInfo();
    }
}