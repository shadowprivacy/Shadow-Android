package su.sres.securesms.jobs;

import androidx.annotation.NonNull;

import su.sres.securesms.crypto.ProfileKeyUtil;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobmanager.impl.NetworkConstraint;
import su.sres.securesms.logging.Log;
import su.sres.securesms.service.IncomingMessageObserver;
import su.sres.securesms.util.Base64;
import su.sres.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.util.guava.Optional;
import su.sres.signalservice.api.SignalServiceMessagePipe;
import su.sres.signalservice.api.SignalServiceMessageReceiver;
import su.sres.signalservice.api.crypto.ProfileCipher;
import su.sres.signalservice.api.profiles.SignalServiceProfile;
import su.sres.signalservice.api.push.SignalServiceAddress;
import su.sres.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;



public class RefreshUnidentifiedDeliveryAbilityJob extends BaseJob  {

    public static final String KEY = "RefreshUnidentifiedDeliveryAbilityJob";

    private static final String TAG = RefreshUnidentifiedDeliveryAbilityJob.class.getSimpleName();

    public RefreshUnidentifiedDeliveryAbilityJob() {
        this(new Job.Parameters.Builder()
                .addConstraint(NetworkConstraint.KEY)
                .setMaxAttempts(10)
                .build());
    }

    private RefreshUnidentifiedDeliveryAbilityJob(@NonNull Job.Parameters parameters) {
        super(parameters);
    }

    @Override
    public @NonNull Data serialize() {
        return Data.EMPTY;
    }

    @Override
    public @NonNull String getFactoryKey() {
        return KEY;
    }

    @Override
    public void onRun() throws Exception {
        byte[]               profileKey = ProfileKeyUtil.getProfileKey(context);
        SignalServiceAddress address    = new SignalServiceAddress(Optional.of(TextSecurePreferences.getLocalUuid(context)), Optional.of(TextSecurePreferences.getLocalNumber(context)));
        SignalServiceProfile profile    = retrieveProfile(address);

        boolean enabled = profile.getUnidentifiedAccess() != null && isValidVerifier(profileKey, profile.getUnidentifiedAccess());

        TextSecurePreferences.setIsUnidentifiedDeliveryEnabled(context, enabled);
        Log.i(TAG, "Set UD status to: " + enabled);
    }

    @Override
    public void onCanceled() {
    }

    @Override
    protected boolean onShouldRetry(@NonNull Exception exception) {
        return exception instanceof PushNetworkException;
    }

    private SignalServiceProfile retrieveProfile(@NonNull SignalServiceAddress address) throws IOException {
        SignalServiceMessageReceiver receiver = ApplicationDependencies.getSignalServiceMessageReceiver();
        SignalServiceMessagePipe     pipe     = IncomingMessageObserver.getPipe();

        if (pipe != null) {
            try {
                return pipe.getProfile(address, Optional.absent());
            } catch (IOException e) {
                Log.w(TAG, e);
            }
        }

        return receiver.retrieveProfile(address, Optional.absent());
    }

    private boolean isValidVerifier(@NonNull byte[] profileKey, @NonNull String verifier) {
        ProfileCipher profileCipher = new ProfileCipher(profileKey);
        try {
            return profileCipher.verifyUnidentifiedAccess(Base64.decode(verifier));
        } catch (IOException e) {
            Log.w(TAG, e);
            return false;
        }
    }

    public static class Factory implements Job.Factory<RefreshUnidentifiedDeliveryAbilityJob> {
        @Override
        public @NonNull RefreshUnidentifiedDeliveryAbilityJob create(@NonNull Parameters parameters, @NonNull Data data) {
            return new RefreshUnidentifiedDeliveryAbilityJob(parameters);
        }
    }
}