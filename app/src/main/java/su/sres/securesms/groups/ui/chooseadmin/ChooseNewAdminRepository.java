package su.sres.securesms.groups.ui.chooseadmin;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import su.sres.securesms.groups.GroupChangeException;
import su.sres.securesms.groups.GroupId;
import su.sres.securesms.groups.GroupManager;
import su.sres.securesms.groups.ui.GroupChangeFailureReason;
import su.sres.securesms.groups.ui.GroupChangeResult;
import su.sres.securesms.recipients.RecipientId;

import java.io.IOException;
import java.util.List;

public final class ChooseNewAdminRepository {
    private final Application context;

    ChooseNewAdminRepository(@NonNull Application context) {
        this.context = context;
    }

    @WorkerThread
    @NonNull
    GroupChangeResult updateAdminsAndLeave(@NonNull GroupId.V2 groupId, @NonNull List<RecipientId> newAdminIds) {
        try {
            GroupManager.addMemberAdminsAndLeaveGroup(context, groupId, newAdminIds);
            return GroupChangeResult.SUCCESS;
        } catch (GroupChangeException | IOException e) {
            return GroupChangeResult.failure(GroupChangeFailureReason.fromException(e));
        }
    }
}