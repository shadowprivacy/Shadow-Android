package su.sres.securesms.migrations;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import su.sres.securesms.jobmanager.JobManager;
import su.sres.core.util.logging.Log;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.securesms.util.TextSecurePreferences;
import su.sres.securesms.util.Util;
import su.sres.securesms.util.VersionTracker;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manages application-level migrations.
 * <p>
 * Migrations can be slotted to occur based on changes in the canonical version code
 * (see {@link Util#getCanonicalVersionCode()}).
 * <p>
 * Migrations are performed via {@link MigrationJob}s. These jobs are durable and are run before any
 * other job, allowing you to schedule safe migrations. Furthermore, you may specify that a
 * migration is UI-blocking, at which point we will show a spinner via
 * {@link ApplicationMigrationActivity} if the user opens the app while the migration is in
 * progress.
 */
public class ApplicationMigrations {

  private static final String TAG = Log.tag(ApplicationMigrations.class);

  private static final MutableLiveData<Boolean> UI_BLOCKING_MIGRATION_RUNNING = new MutableLiveData<>();

  public static final int CURRENT_VERSION = 31;

  private static final class Version {
    static final int VERSIONED_PROFILE      = 15;
    static final int NEW_ACTIVATION_MODEL   = 16;
    static final int TRIM_SETTINGS          = 17;
    static final int GV2                    = 18;
    static final int THUMBNAIL_CLEANUP      = 19;
    static final int GV2_2                  = 20;
    static final int DIR                    = 21;
    static final int BACKUP_NOTIFICATION    = 22;
    static final int GV1_MIGRATION          = 23;
    static final int BLOB_LOCATION          = 24;
    static final int SYSTEM_NAME_SPLIT      = 25;
    static final int DAY_BY_DAY_STICKERS    = 26;
    static final int SFU_CERT               = 27;
    static final int PROFILE_SHARING_UPDATE = 29;
    static final int APPLY_UNIVERSAL_EXPIRE = 30;
    static final int SENDER_KEY             = 31;
  }

  /**
   * This *must* be called after the {@link JobManager} has been instantiated, but *before* the call
   * to {@link JobManager#beginJobLoop()}. Otherwise, other non-migration jobs may have started
   * executing before we add the migration jobs.
   */
  public static void onApplicationCreate(@NonNull Context context, @NonNull JobManager jobManager) {

    if (!isUpdate(context)) {
      Log.d(TAG, "Not an update. Skipping.");
      VersionTracker.updateLastSeenVersion(context);
      return;
    } else {
      Log.d(TAG, "About to update. Clearing deprecation flag.");
      SignalStore.misc().clearClientDeprecated();
    }

    final int lastSeenVersion = TextSecurePreferences.getAppMigrationVersion(context);
    Log.d(TAG, "currentVersion: " + CURRENT_VERSION + ",  lastSeenVersion: " + lastSeenVersion);

    LinkedHashMap<Integer, MigrationJob> migrationJobs = getMigrationJobs(context, lastSeenVersion);

    if (migrationJobs.size() > 0) {
      Log.i(TAG, "About to enqueue " + migrationJobs.size() + " migration(s).");

      boolean uiBlocking        = true;
      int     uiBlockingVersion = lastSeenVersion;

      for (Map.Entry<Integer, MigrationJob> entry : migrationJobs.entrySet()) {
        int          version = entry.getKey();
        MigrationJob job     = entry.getValue();

        uiBlocking &= job.isUiBlocking();
        if (uiBlocking) {
          uiBlockingVersion = version;
        }

        jobManager.add(job);
        jobManager.add(new MigrationCompleteJob(version));
      }

      if (uiBlockingVersion > lastSeenVersion) {
        Log.i(TAG, "Migration set is UI-blocking through version " + uiBlockingVersion + ".");
        UI_BLOCKING_MIGRATION_RUNNING.postValue(true);
      } else {
        Log.i(TAG, "Migration set is non-UI-blocking.");
        UI_BLOCKING_MIGRATION_RUNNING.postValue(false);
      }

      final long startTime = System.currentTimeMillis();
      final int  uiVersion = uiBlockingVersion;

      EventBus.getDefault().register(new Object() {
        @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
        public void onMigrationComplete(MigrationCompleteEvent event) {
          Log.i(TAG, "Received MigrationCompleteEvent for version " + event.getVersion() + ". (Current: " + CURRENT_VERSION + ")");

          if (event.getVersion() > CURRENT_VERSION) {
            throw new AssertionError("Received a higher version than the current version? App downgrades are not supported. (received: " + event.getVersion() + ", current: " + CURRENT_VERSION + ")");
          }

          Log.i(TAG, "Updating last migration version to " + event.getVersion());
          TextSecurePreferences.setAppMigrationVersion(context, event.getVersion());

          if (event.getVersion() == CURRENT_VERSION) {
            Log.i(TAG, "Migration complete. Took " + (System.currentTimeMillis() - startTime) + " ms.");
            EventBus.getDefault().unregister(this);

            VersionTracker.updateLastSeenVersion(context);
            UI_BLOCKING_MIGRATION_RUNNING.postValue(false);
          } else if (event.getVersion() >= uiVersion) {
            Log.i(TAG, "Version is >= the UI-blocking version. Posting 'false'.");
            UI_BLOCKING_MIGRATION_RUNNING.postValue(false);
          }
        }
      });
    } else {
      Log.d(TAG, "No migrations.");
      TextSecurePreferences.setAppMigrationVersion(context, CURRENT_VERSION);
      VersionTracker.updateLastSeenVersion(context);
      UI_BLOCKING_MIGRATION_RUNNING.postValue(false);
    }
  }

  /**
   * @return A {@link LiveData} object that will update with whether or not a UI blocking migration
   * is in progress.
   */
  public static LiveData<Boolean> getUiBlockingMigrationStatus() {
    return UI_BLOCKING_MIGRATION_RUNNING;
  }

  /**
   * @return True if a UI blocking migration is running.
   */
  public static boolean isUiBlockingMigrationRunning() {
    Boolean value = UI_BLOCKING_MIGRATION_RUNNING.getValue();
    return value != null && value;
  }

  /**
   * @return Whether or not we're in the middle of an update, as determined by the last seen and
   * current version.
   */
  public static boolean isUpdate(@NonNull Context context) {
    return TextSecurePreferences.getAppMigrationVersion(context) < CURRENT_VERSION;
  }

  private static LinkedHashMap<Integer, MigrationJob> getMigrationJobs(@NonNull Context context, int lastSeenVersion) {
    LinkedHashMap<Integer, MigrationJob> jobs = new LinkedHashMap<>();

    if (lastSeenVersion < Version.VERSIONED_PROFILE) {
      jobs.put(Version.VERSIONED_PROFILE, new ProfileMigrationJob());
    }

    if (lastSeenVersion < Version.NEW_ACTIVATION_MODEL) {
      jobs.put(Version.NEW_ACTIVATION_MODEL, new LicenseMigrationJob());
    }

    if (lastSeenVersion < Version.TRIM_SETTINGS) {
      jobs.put(Version.TRIM_SETTINGS, new TrimByLengthSettingsMigrationJob());
    }

    if (lastSeenVersion < Version.GV2) {
      jobs.put(Version.GV2, new AttributesMigrationJob());
    }

    if (lastSeenVersion < Version.THUMBNAIL_CLEANUP) {
      jobs.put(Version.THUMBNAIL_CLEANUP, new DatabaseMigrationJob());
    }

    if (lastSeenVersion < Version.GV2_2) {
      jobs.put(Version.GV2_2, new AttributesMigrationJob());
    }

    if (lastSeenVersion < Version.DIR) {
      jobs.put(Version.DIR, new DirectoryRefreshMigrationJob());
    }

    if (lastSeenVersion < Version.BACKUP_NOTIFICATION) {
      jobs.put(Version.BACKUP_NOTIFICATION, new BackupNotificationMigrationJob());
    }

    if (lastSeenVersion < Version.GV1_MIGRATION) {
      jobs.put(Version.GV1_MIGRATION, new AttributesMigrationJob());
    }

    if (lastSeenVersion < Version.BLOB_LOCATION) {
      jobs.put(Version.BLOB_LOCATION, new BlobStorageLocationMigrationJob());
    }

    if (lastSeenVersion < Version.SYSTEM_NAME_SPLIT) {
      jobs.put(Version.SYSTEM_NAME_SPLIT, new DirectoryRefreshMigrationJob());
    }

    if (lastSeenVersion < Version.DAY_BY_DAY_STICKERS) {
      jobs.put(Version.DAY_BY_DAY_STICKERS, new StickerDayByDayMigrationJob());
    }

    if (lastSeenVersion < Version.SFU_CERT) {
      jobs.put(Version.SFU_CERT, new SfuCertJob());
    }

    if (lastSeenVersion < Version.PROFILE_SHARING_UPDATE) {
      jobs.put(Version.PROFILE_SHARING_UPDATE, new ProfileSharingUpdateMigrationJob());
    }

    if (lastSeenVersion < Version.APPLY_UNIVERSAL_EXPIRE) {
      jobs.put(Version.APPLY_UNIVERSAL_EXPIRE, new ApplyUnknownFieldsToSelfMigrationJob());
    }

    if (lastSeenVersion < Version.SENDER_KEY) {
      jobs.put(Version.SENDER_KEY, new AttributesMigrationJob());
    }

    return jobs;
  }
}