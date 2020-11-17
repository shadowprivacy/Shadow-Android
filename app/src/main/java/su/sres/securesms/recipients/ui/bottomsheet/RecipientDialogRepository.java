package su.sres.securesms.recipients.ui.bottomsheet;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Consumer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.GroupDatabase;
import su.sres.securesms.database.IdentityDatabase;
import su.sres.securesms.groups.GroupChangeException;
import su.sres.securesms.groups.GroupId;
import su.sres.securesms.groups.GroupManager;
import su.sres.securesms.groups.ui.GroupChangeErrorCallback;
import su.sres.securesms.groups.ui.GroupChangeFailureReason;
import su.sres.securesms.logging.Log;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.util.concurrent.SignalExecutors;
import su.sres.securesms.util.concurrent.SimpleTask;

final class RecipientDialogRepository {

    private static final String TAG = Log.tag(RecipientDialogRepository.class);

    @NonNull  private final Context     context;
    @NonNull  private final RecipientId recipientId;
    @Nullable private final GroupId     groupId;

    RecipientDialogRepository(@NonNull Context context,
                              @NonNull RecipientId recipientId,
                              @Nullable GroupId groupId)
    {
        this.context     = context;
        this.recipientId = recipientId;
        this.groupId     = groupId;
    }

    @NonNull RecipientId getRecipientId() {
        return recipientId;
    }

    @Nullable GroupId getGroupId() {
        return groupId;
    }

    void getIdentity(@NonNull Consumer<IdentityDatabase.IdentityRecord> callback) {
        SignalExecutors.BOUNDED.execute(
                () -> callback.accept(DatabaseFactory.getIdentityDatabase(context)
                        .getIdentity(recipientId)
                        .orNull()));
    }

    void getRecipient(@NonNull RecipientCallback recipientCallback) {
        SimpleTask.run(SignalExecutors.BOUNDED,
                () -> Recipient.resolved(recipientId),
                recipientCallback::onRecipient);
    }

    void getGroupName(@NonNull Consumer<String> stringConsumer) {
        SimpleTask.run(SignalExecutors.BOUNDED,
                () -> DatabaseFactory.getGroupDatabase(context).requireGroup(Objects.requireNonNull(groupId)).getTitle(),
                stringConsumer::accept);
    }

    void removeMember(@NonNull Consumer<Boolean> onComplete, @NonNull GroupChangeErrorCallback error) {
        SimpleTask.run(SignalExecutors.UNBOUNDED,
                () -> {
                    try {
                        GroupManager.ejectFromGroup(context, Objects.requireNonNull(groupId).requireV2(), Recipient.resolved(recipientId));
                        return true;
                    } catch (GroupChangeException | IOException e) {
                        Log.w(TAG, e);
                        error.onError(GroupChangeFailureReason.fromException(e));
                    }
                    return false;
                },
                onComplete::accept);
    }

    void setMemberAdmin(boolean admin, @NonNull Consumer<Boolean> onComplete, @NonNull GroupChangeErrorCallback error) {
        SimpleTask.run(SignalExecutors.UNBOUNDED,
                () -> {
                    try {
                        GroupManager.setMemberAdmin(context, Objects.requireNonNull(groupId).requireV2(), recipientId, admin);
                        return true;
                    } catch (GroupChangeException | IOException e) {
                        Log.w(TAG, e);
                        error.onError(GroupChangeFailureReason.fromException(e));
                    }
                    return false;
                },
                onComplete::accept);
    }

    void getGroupMembership(@NonNull Consumer<List<RecipientId>> onComplete) {
        SimpleTask.run(SignalExecutors.UNBOUNDED,
                () -> {
                    GroupDatabase                   groupDatabase   = DatabaseFactory.getGroupDatabase(context);
                    List<GroupDatabase.GroupRecord> groupRecords    = groupDatabase.getPushGroupsContainingMember(recipientId);
                    ArrayList<RecipientId>          groupRecipients = new ArrayList<>(groupRecords.size());

                    for (GroupDatabase.GroupRecord groupRecord : groupRecords) {
                        groupRecipients.add(groupRecord.getRecipientId());
                    }

                    return groupRecipients;
                },
                onComplete::accept);
    }

    public void getActiveGroupCount(@NonNull Consumer<Integer> onComplete) {
        SignalExecutors.BOUNDED.execute(() -> onComplete.accept(DatabaseFactory.getGroupDatabase(context).getActiveGroupCount()));
    }

    interface RecipientCallback {
        void onRecipient(@NonNull Recipient recipient);
    }
}