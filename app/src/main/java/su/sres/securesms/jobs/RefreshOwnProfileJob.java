package su.sres.securesms.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import su.sres.securesms.crypto.ProfileKeyUtil;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobmanager.impl.NetworkConstraint;
import su.sres.securesms.logging.Log;
import su.sres.securesms.profiles.ProfileName;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.util.ProfileUtil;
import su.sres.securesms.util.TextSecurePreferences;
import su.sres.signalservice.api.crypto.InvalidCiphertextException;
import su.sres.signalservice.api.profiles.SignalServiceProfile;
import su.sres.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;


/**
 * Refreshes the profile of the local user. Different from {@link RetrieveProfileJob} in that we
 * have to sometimes look at/set different data stores, and we will *always* do the fetch regardless
 * of caching.
 */
public class RefreshOwnProfileJob extends BaseJob {

    public static final String KEY = "RefreshOwnProfileJob";

    private static final String TAG = Log.tag(RefreshOwnProfileJob.class);

    public RefreshOwnProfileJob() {
        this(new Parameters.Builder()
                .addConstraint(NetworkConstraint.KEY)
                .setQueue("RefreshOwnProfileJob")
                .setMaxInstances(1)
                .setMaxAttempts(10)
                .build());
    }


    private RefreshOwnProfileJob(@NonNull Parameters parameters) {
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
    protected void onRun() throws Exception {
        SignalServiceProfile profile = ProfileUtil.retrieveProfile(context, Recipient.self());

        setProfileName(profile.getName());
        setProfileAvatar(profile.getAvatar());
        setProfileCapabilities(profile.getCapabilities());
    }

    @Override
    protected boolean onShouldRetry(@NonNull Exception e) {
        return e instanceof PushNetworkException;
    }

    @Override
    public void onFailure() { }

    private void setProfileName(@Nullable String encryptedName) {
        try {
            byte[]      profileKey    = ProfileKeyUtil.getProfileKey(context);
            String      plaintextName = ProfileUtil.decryptName(profileKey, encryptedName);
            ProfileName profileName   = ProfileName.fromSerialized(plaintextName);

            DatabaseFactory.getRecipientDatabase(context).setProfileName(Recipient.self().getId(), profileName);
            TextSecurePreferences.setProfileName(context, profileName);
        } catch (InvalidCiphertextException | IOException e) {
            Log.w(TAG, e);
        }
    }

    private void setProfileAvatar(@Nullable String avatar) {
        ApplicationDependencies.getJobManager().add(new RetrieveProfileAvatarJob(Recipient.self(), avatar));
    }

    private void setProfileCapabilities(@Nullable SignalServiceProfile.Capabilities capabilities) {
        if (capabilities == null) {
            return;
        }

        DatabaseFactory.getRecipientDatabase(context).setUuidSupported(Recipient.self().getId(), capabilities.isUuid());
    }

    public static final class Factory implements Job.Factory<RefreshOwnProfileJob> {

        @Override
        public @NonNull RefreshOwnProfileJob create(@NonNull Parameters parameters, @NonNull Data data) {
            return new RefreshOwnProfileJob(parameters);
        }
    }
}