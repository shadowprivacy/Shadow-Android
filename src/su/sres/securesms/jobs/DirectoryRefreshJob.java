package su.sres.securesms.jobs;

import android.app.Application;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import su.sres.securesms.database.Address;
import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobmanager.impl.NetworkConstraint;
import su.sres.securesms.logging.Log;

import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.util.DirectoryHelper;
import su.sres.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;

public class DirectoryRefreshJob extends BaseJob {

  public static final String KEY = "DirectoryRefreshJob";

  private static final String TAG = DirectoryRefreshJob.class.getSimpleName();
  private static final String KEY_ADDRESS             = "address";
  private static final String KEY_NOTIFY_OF_NEW_USERS = "notify_of_new_users";

  @Nullable private Recipient recipient;
  private boolean   notifyOfNewUsers;

  public DirectoryRefreshJob(boolean notifyOfNewUsers) {
    this(null, notifyOfNewUsers);
  }

  public DirectoryRefreshJob(@Nullable Recipient recipient,
                             boolean notifyOfNewUsers)
  {
    this(new Job.Parameters.Builder()
                    .setQueue("DirectoryRefreshJob")
                    .addConstraint(NetworkConstraint.KEY)
                    .setMaxAttempts(10)
                    .build(),
            recipient,
            notifyOfNewUsers);
  }

  private DirectoryRefreshJob(@NonNull Job.Parameters parameters, @Nullable Recipient recipient, boolean notifyOfNewUsers) {
    super(parameters);

    this.recipient        = recipient;
    this.notifyOfNewUsers = notifyOfNewUsers;
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder().putString(KEY_ADDRESS, recipient != null ? recipient.getAddress().serialize() : null)
            .putBoolean(KEY_NOTIFY_OF_NEW_USERS, notifyOfNewUsers)
            .build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws IOException {
    Log.i(TAG, "DirectoryRefreshJob.onRun()");

    if (recipient == null) {
      DirectoryHelper.refreshDirectory(context, notifyOfNewUsers);
    } else {
      DirectoryHelper.refreshDirectoryFor(context, recipient);
    }
  }

  @Override
  public boolean onShouldRetry(Exception exception) {
    if (exception instanceof PushNetworkException) return true;
    return false;
  }

  @Override
  public void onCanceled() {}

  public static final class Factory implements Job.Factory<DirectoryRefreshJob> {

    private final Application application;

    public Factory(@NonNull Application application) {
      this.application = application;
    }

    @Override
    public @NonNull DirectoryRefreshJob create(@NonNull Parameters parameters, @NonNull Data data) {
      String    serializedAddress = data.getString(KEY_ADDRESS);
      Address   address           = serializedAddress != null ? Address.fromSerialized(serializedAddress) : null;
      Recipient recipient         = address != null ? Recipient.from(application, address, true) : null;
      boolean   notifyOfNewUsers  = data.getBoolean(KEY_NOTIFY_OF_NEW_USERS);

      return new DirectoryRefreshJob(parameters, recipient, notifyOfNewUsers);
    }
  }
}
