package su.sres.securesms.crypto;

import android.content.Context;

import androidx.annotation.NonNull;

import su.sres.securesms.crypto.storage.SignalSenderKeyStore;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.recipients.Recipient;
import su.sres.signalservice.api.SignalSessionLock;
import su.sres.signalservice.api.push.DistributionId;
import su.sres.signalservice.api.push.SignalServiceAddress;

public final class SenderKeyUtil {
  private SenderKeyUtil() {}

  /**
   * Clears the state for a sender key session we created. It will naturally get re-created when it is next needed, rotating the key.
   */
  public static void rotateOurKey(@NonNull Context context, @NonNull DistributionId distributionId) {
    try (SignalSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      ApplicationDependencies.getSenderKeyStore().deleteAllFor(Recipient.self().getId(), distributionId);
      DatabaseFactory.getSenderKeySharedDatabase(context).deleteAllFor(distributionId);
    }
  }

  /**
   * Gets when the sender key session was created, or -1 if it doesn't exist.
   */
  public static long getCreateTimeForOurKey(@NonNull Context context, @NonNull DistributionId distributionId) {
    return DatabaseFactory.getSenderKeyDatabase(context).getCreatedTime(Recipient.self().getId(), SignalServiceAddress.DEFAULT_DEVICE_ID, distributionId);
  }

  /**
   * Deletes all stored state around session keys. Should only really be used when the user is re-registering.
   */
  public static void clearAllState(@NonNull Context context) {
    try (SignalSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      ApplicationDependencies.getSenderKeyStore().deleteAll();
      DatabaseFactory.getSenderKeySharedDatabase(context).deleteAll();
    }
  }
}
