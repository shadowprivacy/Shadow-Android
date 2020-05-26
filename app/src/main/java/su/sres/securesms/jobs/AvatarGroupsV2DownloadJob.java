package su.sres.securesms.jobs;

import androidx.annotation.NonNull;

import org.signal.zkgroup.VerificationFailedException;
import org.signal.zkgroup.groups.GroupSecretParams;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.GroupDatabase;
import su.sres.securesms.database.GroupDatabase.GroupRecord;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.groups.GroupId;
import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobmanager.impl.NetworkConstraint;
import su.sres.securesms.logging.Log;
import su.sres.securesms.profiles.AvatarHelper;
import su.sres.securesms.util.ByteUnit;
import su.sres.securesms.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;
import su.sres.signalservice.api.SignalServiceMessageReceiver;
import su.sres.signalservice.api.groupsv2.GroupsV2Operations;
import su.sres.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public final class AvatarGroupsV2DownloadJob extends BaseJob {

    public static final String KEY = "AvatarGroupsV2DownloadJob";

    private static final String TAG = Log.tag(AvatarGroupsV2DownloadJob.class);

    private static final long AVATAR_DOWNLOAD_FAIL_SAFE_MAX_SIZE = ByteUnit.MEGABYTES.toBytes(5);

    private static final String KEY_GROUP_ID = "group_id";
    private static final String CDN_KEY      = "cdn_key";

    private final GroupId.V2 groupId;
    private final String     cdnKey;

    public AvatarGroupsV2DownloadJob(@NonNull GroupId.V2 groupId, @NonNull String cdnKey) {
        this(new Parameters.Builder()
                        .addConstraint(NetworkConstraint.KEY)
                        .setQueue("AvatarGroupsV2DownloadJob::" + groupId)
                        .setMaxAttempts(10)
                        .build(),
                groupId,
                cdnKey);
    }

    private AvatarGroupsV2DownloadJob(@NonNull Parameters parameters, @NonNull GroupId.V2 groupId, @NonNull String cdnKey) {
        super(parameters);
        this.groupId = groupId;
        this.cdnKey  = cdnKey;
    }

    @Override
    public @NonNull Data serialize() {
        return new Data.Builder()
                .putString(KEY_GROUP_ID, groupId.toString())
                .putString(CDN_KEY, cdnKey)
                .build();
    }

    @Override
    public @NonNull String getFactoryKey() {
        return KEY;
    }

    @Override
    public void onRun() throws IOException {
        GroupDatabase         database   = DatabaseFactory.getGroupDatabase(context);
        Optional<GroupRecord> record     = database.getGroup(groupId);
        File                  attachment = null;

        try {
            if (!record.isPresent()) {
                Log.w(TAG, "Cannot download avatar for unknown group");
                return;
            }

            attachment = File.createTempFile("avatar", "gv2", context.getCacheDir());
            attachment.deleteOnExit();

            SignalServiceMessageReceiver receiver      = ApplicationDependencies.getSignalServiceMessageReceiver();
            byte[]                       encryptedData;

            try (FileInputStream inputStream = receiver.retrieveGroupsV2ProfileAvatar(cdnKey, attachment, AVATAR_DOWNLOAD_FAIL_SAFE_MAX_SIZE)) {

                encryptedData = new byte[(int) attachment.length()];

                Util.readFully(inputStream, encryptedData);

                GroupsV2Operations                 operations        = ApplicationDependencies.getGroupsV2Operations();
                GroupSecretParams                  groupSecretParams = GroupSecretParams.deriveFromMasterKey(record.get().requireV2GroupProperties().getGroupMasterKey());
                GroupsV2Operations.GroupOperations groupOperations   = operations.forGroup(groupSecretParams);
                byte[]                             decryptedAvatar   = groupOperations.decryptAvatar(encryptedData);

                AvatarHelper.setAvatar(context, record.get().getRecipientId(), decryptedAvatar != null ? new ByteArrayInputStream(decryptedAvatar) : null);
                database.onAvatarUpdated(groupId, true);
            }

        } catch (NonSuccessfulResponseCodeException | VerificationFailedException e) {
            Log.w(TAG, e);
        } finally {
            if (attachment != null && attachment.exists())
                if (!attachment.delete()) {
                    Log.w(TAG, "Unable to delete temp avatar file");
                }
        }
    }

    @Override
    public void onFailure() {}

    @Override
    public boolean onShouldRetry(@NonNull Exception exception) {
        return exception instanceof IOException;
    }

    public static final class Factory implements Job.Factory<AvatarGroupsV2DownloadJob> {
        @Override
        public @NonNull AvatarGroupsV2DownloadJob create(@NonNull Parameters parameters, @NonNull Data data) {
            return new AvatarGroupsV2DownloadJob(parameters,
                    GroupId.parseOrThrow(data.getString(KEY_GROUP_ID)).requireV2(),
                    data.getString(CDN_KEY));
        }
    }
}