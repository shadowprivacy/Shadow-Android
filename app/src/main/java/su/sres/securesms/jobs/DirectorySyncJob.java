package su.sres.securesms.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobmanager.impl.NetworkConstraint;
import su.sres.core.util.logging.Log;

import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.contacts.sync.DirectoryHelper;
import su.sres.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;

// TODO: get rid of notifying of new users

public class DirectorySyncJob extends BaseJob {

    public static final String KEY = "DirectorySyncJob";

    private static final String TAG = Log.tag(DirectorySyncJob.class);
    private static final String KEY_RECIPIENT           = "recipient";
    private static final String KEY_NOTIFY_OF_NEW_USERS = "notify_of_new_users";

    @Nullable private Recipient recipient;
    private boolean   notifyOfNewUsers;

    public DirectorySyncJob(boolean notifyOfNewUsers) {
        this(null, notifyOfNewUsers);
    }

    public DirectorySyncJob(@Nullable Recipient recipient,
                               boolean notifyOfNewUsers)
    {
        this(new Job.Parameters.Builder()
                        .setQueue(StorageSyncJob.QUEUE_KEY)
                        .addConstraint(NetworkConstraint.KEY)
                        .setMaxAttempts(10)
                        .build(),
                recipient,
                notifyOfNewUsers);
    }

    private DirectorySyncJob(@NonNull Job.Parameters parameters, @Nullable Recipient recipient, boolean notifyOfNewUsers) {
        super(parameters);

        this.recipient        = recipient;
        this.notifyOfNewUsers = notifyOfNewUsers;
    }

    @Override
    public @NonNull Data serialize() {
        return new Data.Builder().putString(KEY_RECIPIENT, recipient != null ? recipient.getId().serialize() : null)
                .putBoolean(KEY_NOTIFY_OF_NEW_USERS, notifyOfNewUsers)
                .build();
    }

    @Override
    public @NonNull String getFactoryKey() {
        return KEY;
    }

    @Override
    protected boolean shouldTrace() {
        return true;
    }

    @Override
    public void onRun() throws IOException {
        Log.i(TAG, "DirectorySyncJob.onRun()");

        if (recipient == null) {
            DirectoryHelper.refreshDirectory(context);
        }
    }

    @Override
    public boolean onShouldRetry(@NonNull Exception exception) {
        if (exception instanceof PushNetworkException) return true;
        return false;
    }

    @Override
    public void onFailure() { }

    public static final class Factory implements Job.Factory<DirectorySyncJob> {

        @Override
        public @NonNull DirectorySyncJob create(@NonNull Parameters parameters, @NonNull Data data) {
            String    serialized       = data.hasString(KEY_RECIPIENT) ? data.getString(KEY_RECIPIENT) : null;
            Recipient recipient        = serialized != null ? Recipient.resolved(RecipientId.from(serialized)) : null;
            boolean   notifyOfNewUsers = data.getBoolean(KEY_NOTIFY_OF_NEW_USERS);

            return new DirectorySyncJob(parameters, recipient, notifyOfNewUsers);
        }
    }
}
