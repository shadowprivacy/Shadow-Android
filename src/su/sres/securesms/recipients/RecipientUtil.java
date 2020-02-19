package su.sres.securesms.recipients;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import su.sres.securesms.ApplicationContext;
import su.sres.securesms.contacts.sync.DirectoryHelper;
import su.sres.securesms.database.RecipientDatabase;
import su.sres.securesms.database.RecipientDatabase.RegisteredState;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobs.DirectoryRefreshJob;
import su.sres.securesms.logging.Log;
import su.sres.securesms.util.FeatureFlags;
import org.whispersystems.libsignal.util.guava.Optional;
import su.sres.signalservice.api.push.SignalServiceAddress;

import java.io.IOException;

public class RecipientUtil {

    private static final String TAG = Log.tag(RecipientUtil.class);

    /**
     * This method will do it's best to craft a fully-populated {@link SignalServiceAddress} based on
     * the provided recipient. This includes performing a possible network request if no UUID is
     * available.
     */
    @WorkerThread
    public static @NonNull SignalServiceAddress toSignalServiceAddress(@NonNull Context context, @NonNull Recipient recipient) {
        recipient = recipient.resolve();

        if (!recipient.getUuid().isPresent() && !recipient.getE164().isPresent()) {
            throw new AssertionError(recipient.getId() + " - No UUID or phone number!");
        }

        if (FeatureFlags.UUIDS && !recipient.getUuid().isPresent()) {
            Log.i(TAG, recipient.getId() + " is missing a UUID...");
            try {
                RegisteredState state = DirectoryHelper.refreshDirectoryFor(context, recipient, false);
                recipient = Recipient.resolved(recipient.getId());
                Log.i(TAG, "Successfully performed a UUID fetch for " + recipient.getId() + ". Registered: " + state);
            } catch (IOException e) {
                Log.w(TAG, "Failed to fetch a UUID for " + recipient.getId() + ". Scheduling a future fetch and building an address without one.");
                ApplicationDependencies.getJobManager().add(new DirectoryRefreshJob(recipient, false));
            }
        }

        return new SignalServiceAddress(Optional.fromNullable(recipient.getUuid().orNull()), Optional.fromNullable(recipient.resolve().getE164().orNull()));
    }

    public static boolean isBlockable(@NonNull Recipient recipient) {
        Recipient resolved = recipient.resolve();
        return resolved.isPushGroup() || resolved.hasServiceIdentifier();
    }
}