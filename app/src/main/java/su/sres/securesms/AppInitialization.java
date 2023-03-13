package su.sres.securesms;

import android.content.Context;

import androidx.annotation.NonNull;

import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.insights.InsightsOptOut;
import su.sres.securesms.jobmanager.JobManager;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.core.util.logging.Log;
import su.sres.securesms.migrations.ApplicationMigrations;
import su.sres.securesms.util.TextSecurePreferences;
import su.sres.securesms.util.Util;

/**
 * Rule of thumb: if there's something you want to do on the first app launch that involves
 * persisting state to the database, you'll almost certainly *also* want to do it post backup
 * restore, since a backup restore will wipe the current state of the database.
 */
public final class AppInitialization {

    private static final String TAG = Log.tag(AppInitialization.class);

    private AppInitialization() {}

    public static void onFirstEverAppLaunch(@NonNull Context context) {
        Log.i(TAG, "onFirstEverAppLaunch()");

        InsightsOptOut.userRequestedOptOut(context);
        TextSecurePreferences.setAppMigrationVersion(context, ApplicationMigrations.CURRENT_VERSION);
        TextSecurePreferences.setJobManagerVersion(context, JobManager.CURRENT_VERSION);
        TextSecurePreferences.setLastExperienceVersionCode(context, Util.getCanonicalVersionCode());
        TextSecurePreferences.setHasSeenStickerIntroTooltip(context, true);
        TextSecurePreferences.setPasswordDisabled(context, true);
        TextSecurePreferences.setLastExperienceVersionCode(context, Util.getCanonicalVersionCode());
        TextSecurePreferences.setReadReceiptsEnabled(context, true);
        TextSecurePreferences.setTypingIndicatorsEnabled(context, true);
        TextSecurePreferences.setHasSeenWelcomeScreen(context, false);
        ApplicationDependencies.getMegaphoneRepository().onFirstEverAppLaunch();
        SignalStore.onFirstEverAppLaunch();
    }

    public static void onPostBackupRestore(@NonNull Context context) {
        Log.i(TAG, "onPostBackupRestore()");

        ApplicationDependencies.getMegaphoneRepository().onFirstEverAppLaunch();
        SignalStore.onFirstEverAppLaunch();
        SignalStore.onboarding().clearAll();
    }

    /**
     * Temporary migration method that does the safest bits of {@link #onFirstEverAppLaunch(Context)}
     */
    public static void onRepairFirstEverAppLaunch(@NonNull Context context) {
        Log.w(TAG, "onRepairFirstEverAppLaunch()");

        InsightsOptOut.userRequestedOptOut(context);
        TextSecurePreferences.setAppMigrationVersion(context, ApplicationMigrations.CURRENT_VERSION);
        TextSecurePreferences.setJobManagerVersion(context, JobManager.CURRENT_VERSION);
        TextSecurePreferences.setLastExperienceVersionCode(context, Util.getCanonicalVersionCode());
        TextSecurePreferences.setHasSeenStickerIntroTooltip(context, true);
        TextSecurePreferences.setPasswordDisabled(context, true);
        TextSecurePreferences.setLastExperienceVersionCode(context, Util.getCanonicalVersionCode());
        ApplicationDependencies.getMegaphoneRepository().onFirstEverAppLaunch();
        SignalStore.onFirstEverAppLaunch();
    }
}