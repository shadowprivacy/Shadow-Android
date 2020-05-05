package su.sres.securesms.contacts.sync;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import su.sres.securesms.database.RecipientDatabase.RegisteredState;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobs.StorageSyncJob;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.securesms.logging.Log;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.storage.StorageSyncHelper;
import su.sres.securesms.util.FeatureFlags;

import java.io.IOException;

public class DirectoryHelper {

  private static final String TAG = Log.tag(DirectoryHelper.class);

  @WorkerThread
  public static void refreshDirectory(@NonNull Context context, boolean notifyOfNewUsers) throws IOException {

    if (FeatureFlags.uuids()) {
      // TODO [greyson] Create a DirectoryHelperV2 when appropriate.
      DirectoryHelperV1.refreshDirectory(context, notifyOfNewUsers);
    } else {
      DirectoryHelperV1.refreshDirectory(context, notifyOfNewUsers);
    }
    StorageSyncHelper.scheduleSyncForDataChange();
  }

    @WorkerThread
    public static RegisteredState refreshDirectoryFor (@NonNull Context context, @NonNull Recipient recipient, boolean notifyOfNewUsers) throws IOException {
      RegisteredState originalRegisteredState = recipient.resolve().getRegistered();
      RegisteredState newRegisteredState      = null;
      if (FeatureFlags.uuids()) {
        // TODO [greyson] Create a DirectoryHelperV2 when appropriate.
        newRegisteredState = DirectoryHelperV1.refreshDirectoryFor(context, recipient, notifyOfNewUsers);
      } else {
        newRegisteredState = DirectoryHelperV1.refreshDirectoryFor(context, recipient, notifyOfNewUsers);
      }
      if (newRegisteredState != originalRegisteredState) {
        StorageSyncHelper.scheduleSyncForDataChange();
      }

      return newRegisteredState;
    }
}




