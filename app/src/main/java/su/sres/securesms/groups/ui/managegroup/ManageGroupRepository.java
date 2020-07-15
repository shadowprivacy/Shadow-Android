package su.sres.securesms.groups.ui.managegroup;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.core.util.Consumer;

import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.ThreadDatabase;
import su.sres.securesms.groups.GroupAccessControl;
import su.sres.securesms.groups.GroupChangeBusyException;
import su.sres.securesms.groups.GroupChangeFailedException;
import su.sres.securesms.groups.GroupId;
import su.sres.securesms.groups.GroupInsufficientRightsException;
import su.sres.securesms.groups.GroupManager;
import su.sres.securesms.groups.GroupNotAMemberException;
import su.sres.securesms.groups.MembershipNotSuitableForV2Exception;
import su.sres.securesms.groups.ui.GroupChangeErrorCallback;
import su.sres.securesms.groups.ui.GroupChangeFailureReason;
import su.sres.securesms.logging.Log;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.util.concurrent.SignalExecutors;
import su.sres.securesms.util.concurrent.SimpleTask;

import java.io.IOException;
import java.util.List;

final class ManageGroupRepository {

    private static final String TAG = Log.tag(ManageGroupRepository.class);

    private final Context         context;
    private final GroupId.Push    groupId;

    ManageGroupRepository(@NonNull Context context, @NonNull GroupId.Push groupId) {
        this.context  = context;
        this.groupId  = groupId;
    }

    public GroupId getGroupId() {
        return groupId;
    }

    void getGroupState(@NonNull Consumer<GroupStateResult> onGroupStateLoaded) {
        SignalExecutors.BOUNDED.execute(() -> onGroupStateLoaded.accept(getGroupState()));
    }

    @WorkerThread
    private GroupStateResult getGroupState() {
        ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(context);
        Recipient      groupRecipient = Recipient.externalGroup(context, groupId);
        long           threadId       = threadDatabase.getThreadIdFor(groupRecipient);

        return new GroupStateResult(threadId, groupRecipient);
    }

    void setExpiration(int newExpirationTime, @NonNull GroupChangeErrorCallback error) {
        SignalExecutors.UNBOUNDED.execute(() -> {
            try {
                GroupManager.updateGroupTimer(context, groupId.requirePush(), newExpirationTime);
            } catch (GroupInsufficientRightsException e) {
                Log.w(TAG, e);
                error.onError(GroupChangeFailureReason.NO_RIGHTS);
            } catch (GroupNotAMemberException e) {
                Log.w(TAG, e);
                error.onError(GroupChangeFailureReason.NOT_A_MEMBER);
            } catch (GroupChangeFailedException | GroupChangeBusyException | IOException e) {
                Log.w(TAG, e);
                error.onError(GroupChangeFailureReason.OTHER);
            }
        });
    }

    void applyMembershipRightsChange(@NonNull GroupAccessControl newRights, @NonNull GroupChangeErrorCallback error) {
        SignalExecutors.UNBOUNDED.execute(() -> {
            try {
                GroupManager.applyMembershipAdditionRightsChange(context, groupId.requireV2(), newRights);
            } catch (GroupInsufficientRightsException | GroupNotAMemberException e) {
                Log.w(TAG, e);
                error.onError(GroupChangeFailureReason.NO_RIGHTS);
            } catch (GroupChangeFailedException | GroupChangeBusyException | IOException e) {
                Log.w(TAG, e);
                error.onError(GroupChangeFailureReason.OTHER);
            }
        });
    }

    void applyAttributesRightsChange(@NonNull GroupAccessControl newRights, @NonNull GroupChangeErrorCallback error) {
        SignalExecutors.UNBOUNDED.execute(() -> {
            try {
                GroupManager.applyAttributesRightsChange(context, groupId.requireV2(), newRights);
            } catch (GroupInsufficientRightsException | GroupNotAMemberException e) {
                Log.w(TAG, e);
                error.onError(GroupChangeFailureReason.NO_RIGHTS);
            } catch (GroupChangeFailedException | GroupChangeBusyException | IOException e) {
                Log.w(TAG, e);
                error.onError(GroupChangeFailureReason.OTHER);
            }
        });
    }

    public void getRecipient(@NonNull Consumer<Recipient> recipientCallback) {
        SimpleTask.run(SignalExecutors.BOUNDED,
                () -> Recipient.externalGroup(context, groupId),
                recipientCallback::accept);
    }

    void setMuteUntil(long until) {
        SignalExecutors.BOUNDED.execute(() -> {
            RecipientId recipientId = Recipient.externalGroup(context, groupId).getId();
            DatabaseFactory.getRecipientDatabase(context).setMuted(recipientId, until);
        });
    }

    void addMembers(@NonNull List<RecipientId> selected, @NonNull GroupChangeErrorCallback error) {
        SignalExecutors.UNBOUNDED.execute(() -> {
            try {
                GroupManager.addMembers(context, groupId, selected);
            } catch (GroupInsufficientRightsException | GroupNotAMemberException e) {
                Log.w(TAG, e);
                error.onError(GroupChangeFailureReason.NO_RIGHTS);
            } catch (GroupChangeFailedException | GroupChangeBusyException | IOException e) {
                Log.w(TAG, e);
                error.onError(GroupChangeFailureReason.OTHER);
            } catch (MembershipNotSuitableForV2Exception e) {
                Log.w(TAG, e);
                error.onError(GroupChangeFailureReason.NOT_CAPABLE);
            }
        });
    }

    static final class GroupStateResult {

        private final long      threadId;
        private final Recipient recipient;

        private GroupStateResult(long threadId,
                                 Recipient recipient)
        {
            this.threadId  = threadId;
            this.recipient = recipient;
        }

        long getThreadId() {
            return threadId;
        }

        Recipient getRecipient() {
            return recipient;
        }
    }

}