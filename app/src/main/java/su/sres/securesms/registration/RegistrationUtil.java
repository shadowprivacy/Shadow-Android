package su.sres.securesms.registration;

import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobs.DirectoryRefreshJob;
import su.sres.securesms.jobs.StorageSyncJob;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.securesms.logging.Log;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.storage.StorageSyncHelper;

public final class RegistrationUtil {

    private static final String TAG = Log.tag(RegistrationUtil.class);

    private RegistrationUtil() {}

    /**
     * There's several events where a registration may or may not be considered complete based on what
     * path a user has taken. This will only truly mark registration as complete if all of the
     * requirements are met.
     */
    public static void markRegistrationPossiblyComplete() {
        if (!SignalStore.registrationValues().isRegistrationComplete() && !Recipient.self().getProfileName().isEmpty()) {
            Log.i(TAG, "Marking registration completed.", new Throwable());
            SignalStore.registrationValues().setRegistrationComplete();
            ApplicationDependencies.getJobManager().startChain(new StorageSyncJob())
                    .then(new DirectoryRefreshJob(false))
                    .enqueue();
        } else if (!SignalStore.registrationValues().isRegistrationComplete()) {
            Log.i(TAG, "Registration is not yet complete.", new Throwable());
        }
    }
}