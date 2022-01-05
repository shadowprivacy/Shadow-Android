package su.sres.securesms.groups.ui.invitesandrequests.joining;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import su.sres.storageservice.protos.groups.AccessControl;
import su.sres.storageservice.protos.groups.local.DecryptedGroupJoinInfo;
import org.signal.zkgroup.VerificationFailedException;
import su.sres.securesms.groups.GroupManager;
import su.sres.securesms.groups.v2.GroupInviteLinkUrl;
import su.sres.securesms.jobs.AvatarGroupsV2DownloadJob;
import su.sres.securesms.logging.Log;
import su.sres.securesms.util.concurrent.SignalExecutors;
import su.sres.signalservice.api.groupsv2.GroupLinkNotActiveException;

import java.io.IOException;

final class GroupJoinRepository {

    private static final String TAG = Log.tag(GroupJoinRepository.class);

    private final Context            context;
    private final GroupInviteLinkUrl groupInviteLinkUrl;

    GroupJoinRepository(@NonNull Context context, @NonNull GroupInviteLinkUrl groupInviteLinkUrl) {
        this.context            = context;
        this.groupInviteLinkUrl = groupInviteLinkUrl;
    }

    void getGroupDetails(@NonNull GetGroupDetailsCallback callback) {
        SignalExecutors.UNBOUNDED.execute(() -> {
            try {
                callback.onComplete(getGroupDetails());
            } catch (IOException e) {
                callback.onError(FetchGroupDetailsError.NetworkError);
            } catch (VerificationFailedException | GroupLinkNotActiveException e) {
                callback.onError(FetchGroupDetailsError.GroupLinkNotActive);
            }
        });
    }

    @WorkerThread
    private @NonNull GroupDetails getGroupDetails()
            throws VerificationFailedException, IOException, GroupLinkNotActiveException
    {
        DecryptedGroupJoinInfo joinInfo = GroupManager.getGroupJoinInfoFromServer(context,
                groupInviteLinkUrl.getGroupMasterKey(),
                groupInviteLinkUrl.getPassword());

        byte[]  avatarBytes           = tryGetAvatarBytes(joinInfo);
        boolean requiresAdminApproval = joinInfo.getAddFromInviteLink() == AccessControl.AccessRequired.ADMINISTRATOR;

        return new GroupDetails(joinInfo.getTitle(),
                avatarBytes,
                joinInfo.getMemberCount(),
                requiresAdminApproval,
                joinInfo.getRevision());
    }

    private @Nullable byte[] tryGetAvatarBytes(@NonNull DecryptedGroupJoinInfo joinInfo) {
        try {
            return AvatarGroupsV2DownloadJob.downloadGroupAvatarBytes(context, groupInviteLinkUrl.getGroupMasterKey(), joinInfo.getAvatar());
        } catch (IOException e) {
            Log.w(TAG, "Failed to get group avatar", e);
            return null;
        }
    }

    interface GetGroupDetailsCallback {
        void onComplete(@NonNull GroupDetails groupDetails);
        void onError(@NonNull FetchGroupDetailsError error);
    }
}