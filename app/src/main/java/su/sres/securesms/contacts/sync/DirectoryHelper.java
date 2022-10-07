package su.sres.securesms.contacts.sync;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;


import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.RecipientDatabase;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobs.MultiDeviceContactUpdateJob;
import su.sres.securesms.jobs.RotateProfileKeyJob;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.securesms.logging.Log;
import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.registration.RegistrationUtil;
import su.sres.securesms.util.Stopwatch;
import su.sres.securesms.util.TextSecurePreferences;
import su.sres.signalservice.api.SignalServiceAccountManager;
import su.sres.signalservice.api.storage.protos.DirectoryResponse;
import su.sres.signalservice.internal.util.JsonUtil;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Manages the up-to-date state of the local directory.
 */
public class DirectoryHelper {

  private static final String TAG = Log.tag(DirectoryHelper.class);

  @WorkerThread
  public static void refreshDirectory(@NonNull Context context) throws IOException {

    if (TextUtils.isEmpty(TextSecurePreferences.getLocalNumber(context))) {
      Log.w(TAG, "Have not yet set our own local number. Skipping.");
      return;
    }

    if (!SignalStore.registrationValues().isRegistrationComplete()) {
      Log.w(TAG, "Registration is not yet complete. Skipping, but running a routine to possibly mark it complete.");
      RegistrationUtil.maybeMarkRegistrationComplete(context);
      return;
    }

    Stopwatch         stopwatch         = new Stopwatch("full");

    final RecipientDatabase recipientDatabase = DatabaseFactory.getRecipientDatabase(context);
    final SignalServiceAccountManager accountManager = ApplicationDependencies.getSignalServiceAccountManager();
    PlainDirectoryResult directoryResult = getDirectoryResult(context, accountManager, false);

    stopwatch.split("network");

    long remoteVersion = directoryResult.getVersion();

    if (directoryResult.isUpdate()) {

      int inserted = 0,
          removed = 0;

      if (directoryResult.isFullUpdate()) {

        // perform full update

        boolean uuidSupported = true;

        Set<String> currentUserLogins = recipientDatabase.getAllUserLogins();
        Map<String, String> fullUpdate = directoryResult.getUpdateContents().get();
        Set<String> userLoginsToInsert = fullUpdate.keySet();

        for(String userLogin : currentUserLogins) {

          RecipientId id = recipientDatabase.getOrInsertFromUserLogin(userLogin);

          if (!userLoginsToInsert.contains(userLogin)) {
            recipientDatabase.markUnregistered(id);
            recipientDatabase.setProfileSharing(id, false);
            removed++;
          }
        }

        for(Map.Entry<String, String> entry : fullUpdate.entrySet()) {

          RecipientId id = recipientDatabase.getOrInsertFromUserLogin(entry.getKey());
          String field = entry.getValue();
          UUID uuid = null;

          if (field.equals("")) {
            uuidSupported = false;
          } else {
            DirectoryEntryValue entryValue = JsonUtil.fromJson(field, DirectoryEntryValue.class);
            uuid = entryValue.getUuid();
          }

          recipientDatabase.markRegistered(id, uuid);
          recipientDatabase.setProfileSharing(id, true);
          inserted++;
        }

        Log.i(TAG, String.format("Full update to version %s successful. Inserted %s entries, removed %s entries", remoteVersion, inserted, removed));

        // since we've got and recorded full directory while uuid is supported on the server side, we can assume the migration is done
        if (uuidSupported) SignalStore.misc().setDirectoryMigratedToUuids(true);

      } else {
        // perform incremental update with subsequent UUID migration (if necessary)

        boolean isUuidMigrated = SignalStore.misc().getDirectoryMigratedToUuids();
        boolean toMigrate = false;

        Map<String, String> incrementalUpdate = directoryResult.getUpdateContents().get();

        for (Map.Entry<String, String> entry : incrementalUpdate.entrySet()) {

          String userLogin = entry.getKey();

          // this will effectively skip an empty incremental update
          if (userLogin.equals("")) continue;

          String field = entry.getValue();
          UUID uuid = null;
          RecipientId id = recipientDatabase.getOrInsertFromUserLogin(userLogin);

          // removal
          if (field.equals("-1")) {
            recipientDatabase.markUnregistered(id);
            recipientDatabase.setProfileSharing(id, false);
            removed++;
          } else {

            if (!field.equals("")) {
              DirectoryEntryValue entryValue = JsonUtil.fromJson(field, DirectoryEntryValue.class);
              uuid = entryValue.getUuid();
            }

            // this will be triggered only for old installations which were already active when UUIDs were not yet supported on the server side
            if (!isUuidMigrated && (uuid != null)) {
              toMigrate = true;
            }

            recipientDatabase.markRegistered(id, uuid);
            recipientDatabase.setProfileSharing(id, true);
            inserted++;
          }
        }

        Log.i(TAG, String.format("Incremental update to version %s successful. Inserted %s entries, removed %s entries", remoteVersion, inserted, removed));

        if (toMigrate) {
          // perform forced full update

          Log.i(TAG, "Server now supports UUIDs in directory! Proceeding to migration.");

          PlainDirectoryResult result = getDirectoryResult(context, accountManager, true);

            int ins = 0;

            Map<String, String> fullUpdate = result.getUpdateContents().get();

            for(Map.Entry<String, String> entry : fullUpdate.entrySet()) {

              RecipientId id = recipientDatabase.getOrInsertFromUserLogin(entry.getKey());
              DirectoryEntryValue entryValue = JsonUtil.fromJson(entry.getValue(), DirectoryEntryValue.class);
              UUID uuid = entryValue.getUuid();

              recipientDatabase.markRegistered(id, uuid);
              recipientDatabase.setProfileSharing(id, true);
              ins++;
            }

            Log.i(TAG, String.format("Directory migration successful. Inserted UUIDs for %s entries", ins));
            SignalStore.misc().setDirectoryMigratedToUuids(true);
        }
      }

      SignalStore.serviceConfigurationValues().setCurrentDirVer(remoteVersion);
      if(removed > 0) ApplicationDependencies.getJobManager().add(new RotateProfileKeyJob());
    }

    // TODO: deal with this later
    if (TextSecurePreferences.isMultiDevice(context)) {
      ApplicationDependencies.getJobManager().add(new MultiDeviceContactUpdateJob());
    }

    TextSecurePreferences.setHasSuccessfullyRetrievedDirectory(context, true);
 //   StorageSyncHelper.scheduleSyncForDataChange();

    stopwatch.split("disk");
    stopwatch.stop(TAG);
  }

  public static PlainDirectoryResult getDirectoryResult(@NonNull Context context, @NonNull SignalServiceAccountManager accountManager, boolean forceFull)
          throws IOException
  {
    DirectoryResponse directoryResponse = accountManager.getDirectoryResponse(SignalStore.serviceConfigurationValues().getCurrentDirVer(), forceFull);

  //  TextSecurePreferences.setHasSuccessfullyRetrievedDirectory(context, true);
    return new PlainDirectoryResult(directoryResponse);
  }
}




