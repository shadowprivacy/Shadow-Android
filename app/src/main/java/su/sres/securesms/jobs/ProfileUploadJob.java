package su.sres.securesms.jobs;

import android.content.Context;

import androidx.annotation.NonNull;

import su.sres.securesms.crypto.ProfileKeyUtil;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobmanager.impl.NetworkConstraint;
import su.sres.securesms.profiles.AvatarHelper;
import su.sres.securesms.profiles.ProfileName;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.util.MediaUtil;
import su.sres.securesms.util.TextSecurePreferences;
import su.sres.securesms.util.Util;
import su.sres.signalservice.api.SignalServiceAccountManager;
import su.sres.signalservice.api.util.StreamDetails;

import java.io.ByteArrayInputStream;

public final class ProfileUploadJob extends BaseJob {

    public static final String KEY = "ProfileUploadJob";

    private final Context                     context;
    private final SignalServiceAccountManager accountManager;

    public ProfileUploadJob() {
        this(new Job.Parameters.Builder()
                .addConstraint(NetworkConstraint.KEY)
                .setQueue(KEY)
                .setLifespan(Parameters.IMMORTAL)
                .setMaxAttempts(Parameters.UNLIMITED)
                .setMaxInstances(1)
                .build());
    }

    private ProfileUploadJob(@NonNull Parameters parameters) {
        super(parameters);

        this.context        = ApplicationDependencies.getApplication();
        this.accountManager = ApplicationDependencies.getSignalServiceAccountManager();
    }

    @Override
    protected void onRun() throws Exception {
        uploadProfileName();
        uploadAvatar();
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

    private void uploadProfileName() throws Exception {
        ProfileName profileName = TextSecurePreferences.getProfileName(context);
        accountManager.setProfileName(ProfileKeyUtil.getProfileKey(context), profileName.serialize());
    }

    private void uploadAvatar() throws Exception {
        final RecipientId selfId = Recipient.self().getId();
        final byte[]      avatar;

        if (AvatarHelper.getAvatarFile(context, selfId).exists() && AvatarHelper.getAvatarFile(context, selfId).length() > 0) {
            avatar = Util.readFully(AvatarHelper.getInputStreamFor(context, Recipient.self().getId()));
        } else {
            avatar = null;
        }

        final StreamDetails avatarDetails;
        if (avatar == null || avatar.length == 0) {
            avatarDetails = null;
        } else {
            avatarDetails = new StreamDetails(new ByteArrayInputStream(avatar),
                    MediaUtil.IMAGE_JPEG,
                    avatar.length);
        }

        accountManager.setProfileAvatar(ProfileKeyUtil.getProfileKey(context), avatarDetails);
    }

    public static class Factory implements Job.Factory {

        @NonNull
        @Override
        public Job create(@NonNull Parameters parameters, @NonNull Data data) {
            return new ProfileUploadJob(parameters);
        }
    }
}