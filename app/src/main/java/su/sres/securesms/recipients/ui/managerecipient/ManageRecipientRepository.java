package su.sres.securesms.recipients.ui.managerecipient;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.core.util.Consumer;

import com.annimon.stream.Stream;

import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.GroupDatabase;
import su.sres.securesms.database.IdentityDatabase;
import su.sres.securesms.database.ThreadDatabase;
import su.sres.core.util.logging.Log;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientId;
import su.sres.core.util.concurrent.SignalExecutors;

import java.util.ArrayList;
import java.util.List;

final class ManageRecipientRepository {

    private static final String TAG = Log.tag(ManageRecipientRepository.class);

    private final Context     context;
    private final RecipientId recipientId;

    ManageRecipientRepository(@NonNull Context context, @NonNull RecipientId recipientId) {
        this.context     = context;
        this.recipientId = recipientId;
    }

    public RecipientId getRecipientId() {
        return recipientId;
    }

    void getThreadId(@NonNull Consumer<Long> onGetThreadId) {
        SignalExecutors.BOUNDED.execute(() -> onGetThreadId.accept(getThreadId()));
    }

    @WorkerThread
    private long getThreadId() {
        ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(context);
        Recipient      groupRecipient = Recipient.resolved(recipientId);

        return threadDatabase.getThreadIdFor(groupRecipient);
    }

    void getIdentity(@NonNull Consumer<IdentityDatabase.IdentityRecord> callback) {
        SignalExecutors.BOUNDED.execute(() -> callback.accept(DatabaseFactory.getIdentityDatabase(context)
                .getIdentity(recipientId)
                .orNull()));
    }

    void getGroupMembership(@NonNull Consumer<List<RecipientId>> onComplete) {
        SignalExecutors.BOUNDED.execute(() -> {
            GroupDatabase                   groupDatabase   = DatabaseFactory.getGroupDatabase(context);
            List<GroupDatabase.GroupRecord> groupRecords    = groupDatabase.getPushGroupsContainingMember(recipientId);
            ArrayList<RecipientId>          groupRecipients = new ArrayList<>(groupRecords.size());

            for (GroupDatabase.GroupRecord groupRecord : groupRecords) {
                groupRecipients.add(groupRecord.getRecipientId());
            }

            onComplete.accept(groupRecipients);
        });
    }

    public void getRecipient(@NonNull Consumer<Recipient> recipientCallback) {
        SignalExecutors.BOUNDED.execute(() -> recipientCallback.accept(Recipient.resolved(recipientId)));
    }

    void setMuteUntil(long until) {
        SignalExecutors.BOUNDED.execute(() -> DatabaseFactory.getRecipientDatabase(context).setMuted(recipientId, until));
    }

    @WorkerThread
    @NonNull List<Recipient> getSharedGroups(@NonNull RecipientId recipientId) {
        return Stream.of(DatabaseFactory.getGroupDatabase(context)
                .getPushGroupsContainingMember(recipientId))
                .filter(g -> g.getMembers().contains(Recipient.self().getId()))
                .map(GroupDatabase.GroupRecord::getRecipientId)
                .map(Recipient::resolved)
                .sortBy(gr -> gr.getDisplayName(context))
                .toList();
    }

    void getActiveGroupCount(@NonNull Consumer<Integer> onComplete) {
        SignalExecutors.BOUNDED.execute(() -> onComplete.accept(DatabaseFactory.getGroupDatabase(context).getActiveGroupCount()));
    }
}