package su.sres.securesms.groups.ui.invitesandrequests.joining;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import su.sres.securesms.groups.GroupChangeBusyException;
import su.sres.securesms.groups.GroupChangeFailedException;
import su.sres.securesms.groups.MembershipNotSuitableForV2Exception;
import su.sres.securesms.util.AsynchronousCallback;
import su.sres.storageservice.protos.groups.local.DecryptedGroupJoinInfo;
import org.signal.zkgroup.VerificationFailedException;
import su.sres.securesms.groups.GroupManager;
import su.sres.securesms.groups.v2.GroupInviteLinkUrl;
import su.sres.securesms.jobs.AvatarGroupsV2DownloadJob;
import su.sres.core.util.logging.Log;
import su.sres.core.util.concurrent.SignalExecutors;
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

    void getGroupDetails(@NonNull AsynchronousCallback.WorkerThread<GroupDetails, FetchGroupDetailsError> callback) {
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

    void joinGroup(@NonNull GroupDetails groupDetails,
                   @NonNull AsynchronousCallback.WorkerThread<JoinGroupSuccess, JoinGroupError> callback)
    {
        SignalExecutors.UNBOUNDED.execute(() -> {
            try {
                GroupManager.GroupActionResult groupActionResult = GroupManager.joinGroup(context,
                        groupInviteLinkUrl.getGroupMasterKey(),
                        groupInviteLinkUrl.getPassword(),
                        groupDetails.getJoinInfo(),
                        groupDetails.getAvatarBytes());

                callback.onComplete(new JoinGroupSuccess(groupActionResult.getGroupRecipient(), groupActionResult.getThreadId()));
            } catch (IOException e) {
                callback.onError(JoinGroupError.NETWORK_ERROR);
            } catch (GroupChangeBusyException e) {
                callback.onError(JoinGroupError.BUSY);
            } catch (GroupLinkNotActiveException e) {
                callback.onError(JoinGroupError.GROUP_LINK_NOT_ACTIVE);
            } catch (GroupChangeFailedException | MembershipNotSuitableForV2Exception e) {
                callback.onError(JoinGroupError.FAILED);
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

        byte[] avatarBytes = tryGetAvatarBytes(joinInfo);

        return new GroupDetails(joinInfo, avatarBytes);
    }

    private @Nullable byte[] tryGetAvatarBytes(@NonNull DecryptedGroupJoinInfo joinInfo) {
        try {
            return AvatarGroupsV2DownloadJob.downloadGroupAvatarBytes(context, groupInviteLinkUrl.getGroupMasterKey(), joinInfo.getAvatar());
        } catch (IOException e) {
            Log.w(TAG, "Failed to get group avatar", e);
            return null;
        }
    }
}