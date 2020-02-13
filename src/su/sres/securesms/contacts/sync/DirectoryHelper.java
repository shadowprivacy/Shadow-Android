package su.sres.securesms.contacts.sync;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import su.sres.securesms.database.RecipientDatabase.RegisteredState;
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
  }

    @WorkerThread
    public static RegisteredState refreshDirectoryFor (@NonNull Context context, @NonNull Recipient recipient, boolean notifyOfNewUsers) throws IOException {
      if (FeatureFlags.UUIDS) {
        // TODO [greyson] Create a DirectoryHelperV2 when appropriate.
        return DirectoryHelperV1.refreshDirectoryFor(context, recipient, notifyOfNewUsers);
      } else {
        return DirectoryHelperV1.refreshDirectoryFor(context, recipient, notifyOfNewUsers);
      }
    }
}




