package su.sres.securesms.contacts.sync;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import su.sres.securesms.database.RecipientDatabase.RegisteredState;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobs.StorageSyncJob;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.util.FeatureFlags;

import java.io.IOException;

public class DirectoryHelper {

  @WorkerThread
  public static void refreshDirectory(@NonNull Context context, boolean notifyOfNewUsers) throws IOException {
    if (FeatureFlags.UUIDS) {
      // TODO [greyson] Create a DirectoryHelperV2 when appropriate.
      DirectoryHelperV1.refreshDirectory(context, notifyOfNewUsers);
    } else {
      DirectoryHelperV1.refreshDirectory(context, notifyOfNewUsers);
    }
    if (FeatureFlags.STORAGE_SERVICE) {
      ApplicationDependencies.getJobManager().add(new StorageSyncJob());
    }
  }

    @WorkerThread
    public static RegisteredState refreshDirectoryFor (@NonNull Context context, @NonNull Recipient recipient, boolean notifyOfNewUsers) throws IOException {
      RegisteredState originalRegisteredState = recipient.resolve().getRegistered();
      RegisteredState newRegisteredState      = null;
      if (FeatureFlags.UUIDS) {
        // TODO [greyson] Create a DirectoryHelperV2 when appropriate.
        newRegisteredState = DirectoryHelperV1.refreshDirectoryFor(context, recipient, notifyOfNewUsers);
      } else {
        newRegisteredState = DirectoryHelperV1.refreshDirectoryFor(context, recipient, notifyOfNewUsers);
      }
      if (FeatureFlags.STORAGE_SERVICE && newRegisteredState != originalRegisteredState) {
        ApplicationDependencies.getJobManager().add(new StorageSyncJob());
      }

      return newRegisteredState;
    }
}




