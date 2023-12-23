package su.sres.securesms.registration;

import android.content.Context;

import androidx.annotation.NonNull;

import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobs.DirectorySyncJob;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.core.util.logging.Log;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.util.TextSecurePreferences;

public final class RegistrationUtil {

    private static final String TAG = Log.tag(RegistrationUtil.class);

    private RegistrationUtil() {}

    /**
     * There's several events where a registration may or may not be considered complete based on what
     * path a user has taken. This will only truly mark registration as complete if all of the
     * requirements are met.
     */
    public static void maybeMarkRegistrationComplete(@NonNull Context context) {
        if (!SignalStore.registrationValues().isRegistrationComplete() &&
                TextSecurePreferences.isPushRegistered(context)        &&
                !Recipient.self().getProfileName().isEmpty())
        {
            Log.i(TAG, "Marking registration completed.", new Throwable());
            SignalStore.registrationValues().setRegistrationComplete();
         //   ApplicationDependencies.getJobManager().startChain(StorageSyncJob.create())
         //                                          .then(new DirectorySyncJob(false))
            ApplicationDependencies.getJobManager().startChain(new DirectorySyncJob(false))
                                                   .enqueue();
        } else if (!SignalStore.registrationValues().isRegistrationComplete()) {
            Log.i(TAG, "Registration is not yet complete.", new Throwable());
        }
    }
}