package su.sres.securesms;

import android.content.Context;

import androidx.annotation.NonNull;

import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.insights.InsightsOptOut;
import su.sres.securesms.jobmanager.JobManager;
import su.sres.securesms.jobs.StickerPackDownloadJob;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.securesms.logging.Log;
import su.sres.securesms.migrations.ApplicationMigrations;
import su.sres.securesms.stickers.BlessedPacks;
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
        ApplicationDependencies.getMegaphoneRepository().onFirstEverAppLaunch();
        SignalStore.onFirstEverAppLaunch();

//   moved to Code Verification stage
//        ApplicationDependencies.getJobManager().add(StickerPackDownloadJob.forInstall(BlessedPacks.ZOZO.getPackId(), BlessedPacks.ZOZO.getPackKey(), false));
//        ApplicationDependencies.getJobManager().add(StickerPackDownloadJob.forInstall(BlessedPacks.BANDIT.getPackId(), BlessedPacks.BANDIT.getPackKey(), false));
//        ApplicationDependencies.getJobManager().add(StickerPackDownloadJob.forReference(BlessedPacks.SWOON_HANDS.getPackId(), BlessedPacks.SWOON_HANDS.getPackKey()));
//        ApplicationDependencies.getJobManager().add(StickerPackDownloadJob.forReference(BlessedPacks.SWOON_FACES.getPackId(), BlessedPacks.SWOON_FACES.getPackKey()));
    }

    public static void onPostBackupRestore(@NonNull Context context) {
        Log.i(TAG, "onPostBackupRestore()");

        ApplicationDependencies.getMegaphoneRepository().onFirstEverAppLaunch();
        SignalStore.onFirstEverAppLaunch();

//   moved to Code Verification stage
//        ApplicationDependencies.getJobManager().add(StickerPackDownloadJob.forInstall(BlessedPacks.ZOZO.getPackId(), BlessedPacks.ZOZO.getPackKey(), false));
//        ApplicationDependencies.getJobManager().add(StickerPackDownloadJob.forInstall(BlessedPacks.BANDIT.getPackId(), BlessedPacks.BANDIT.getPackKey(), false));
//        ApplicationDependencies.getJobManager().add(StickerPackDownloadJob.forReference(BlessedPacks.SWOON_HANDS.getPackId(), BlessedPacks.SWOON_HANDS.getPackKey()));/
//        ApplicationDependencies.getJobManager().add(StickerPackDownloadJob.forReference(BlessedPacks.SWOON_FACES.getPackId(), BlessedPacks.SWOON_FACES.getPackKey()));
    }
}