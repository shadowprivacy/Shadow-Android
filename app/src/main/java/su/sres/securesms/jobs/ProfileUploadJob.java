package su.sres.securesms.jobs;

import android.content.Context;

import androidx.annotation.NonNull;

import su.sres.securesms.logging.Log;
import su.sres.securesms.push.SignalServiceNetworkAccess;
import org.signal.zkgroup.profiles.ProfileKey;

import java.util.concurrent.TimeUnit;

import su.sres.securesms.crypto.ProfileKeyUtil;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobmanager.impl.NetworkConstraint;
import su.sres.securesms.profiles.AvatarHelper;
import su.sres.securesms.profiles.ProfileName;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.util.FeatureFlags;
import su.sres.securesms.util.TextSecurePreferences;
import su.sres.signalservice.api.SignalServiceAccountManager;
import su.sres.signalservice.api.util.StreamDetails;

public final class ProfileUploadJob extends BaseJob {

    private static final String TAG = Log.tag(ProfileUploadJob.class);

    public static final String KEY = "ProfileUploadJob";

    public static final String QUEUE = "ProfileAlteration";

    private final Context                     context;
    private final SignalServiceAccountManager accountManager;

    public ProfileUploadJob() {
        this(new Job.Parameters.Builder()
                .addConstraint(NetworkConstraint.KEY)
                .setQueue(QUEUE)
                .setLifespan(TimeUnit.DAYS.toMillis(30))
                .setMaxAttempts(Parameters.UNLIMITED)
                .setMaxInstances(2)
                .build());
    }

    private ProfileUploadJob(@NonNull Parameters parameters) {
        super(parameters);

        this.context        = ApplicationDependencies.getApplication();
        this.accountManager = ApplicationDependencies.getSignalServiceAccountManager();
    }

    @Override
    protected void onRun() throws Exception {
        if (!TextSecurePreferences.isPushRegistered(context)) {
            Log.w(TAG, "Not registered. Skipping.");
            return;
        }

        ProfileKey  profileKey  = ProfileKeyUtil.getSelfProfileKey();
        ProfileName profileName = Recipient.self().getProfileName();
        String      avatarPath;

        accountManager.updatePushServiceSocket(new SignalServiceNetworkAccess(context).getConfiguration(context));

        try (StreamDetails avatar = AvatarHelper.getSelfProfileAvatarStream(context)) {
            avatarPath = accountManager.setVersionedProfile(Recipient.self().getUuid().get(), profileKey, profileName.serialize(), avatar).orNull();
        }

        DatabaseFactory.getRecipientDatabase(context).setProfileAvatar(Recipient.self().getId(), avatarPath);
    }

    @Override
    protected boolean onShouldRetry(@NonNull Exception e) {
        return true;
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
    public void onFailure() {
    }

    public static class Factory implements Job.Factory<ProfileUploadJob> {

        @Override
        public @NonNull ProfileUploadJob create(@NonNull Parameters parameters, @NonNull Data data) {
            return new ProfileUploadJob(parameters);
        }
    }
}