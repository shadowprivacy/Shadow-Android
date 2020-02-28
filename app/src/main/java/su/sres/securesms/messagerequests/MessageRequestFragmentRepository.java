package su.sres.securesms.messagerequests;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;

import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.GroupDatabase;
import su.sres.securesms.database.MessagingDatabase;
import su.sres.securesms.database.MmsSmsDatabase;
import su.sres.securesms.database.RecipientDatabase;
import su.sres.securesms.database.ThreadDatabase;
import su.sres.securesms.database.model.MessageRecord;
import su.sres.securesms.notifications.MarkReadReceiver;
import su.sres.securesms.notifications.MessageNotifier;
import su.sres.securesms.recipients.LiveRecipient;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.recipients.RecipientUtil;
import su.sres.securesms.util.concurrent.SignalExecutors;
import su.sres.securesms.util.concurrent.SimpleTask;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.List;

public class MessageRequestFragmentRepository {

    private final Context       context;
    private final RecipientId   recipientId;
    private final long          threadId;
    private final LiveRecipient liveRecipient;

    public MessageRequestFragmentRepository(@NonNull Context context, @NonNull RecipientId recipientId, long threadId) {
        this.context       = context.getApplicationContext();
        this.recipientId   = recipientId;
        this.threadId      = threadId;
        this.liveRecipient = Recipient.live(recipientId);
    }

    public LiveRecipient getLiveRecipient() {
        return liveRecipient;
    }

    public void refreshRecipient() {
        SignalExecutors.BOUNDED.execute(liveRecipient::refresh);
    }

    public void getMessageRecord(@NonNull Consumer<MessageRecord> onMessageRecordLoaded) {
        SimpleTask.run(() -> {
            MmsSmsDatabase mmsSmsDatabase = DatabaseFactory.getMmsSmsDatabase(context);
            try (Cursor cursor = mmsSmsDatabase.getConversation(threadId, 0, 1)) {
                if (!cursor.moveToFirst()) return null;
                return mmsSmsDatabase.readerFor(cursor).getCurrent();
            }
        }, onMessageRecordLoaded::accept);
    }

    public void getGroups(@NonNull Consumer<List<String>> onGroupsLoaded) {
        SimpleTask.run(() -> {
            GroupDatabase groupDatabase = DatabaseFactory.getGroupDatabase(context);
            return groupDatabase.getGroupNamesContainingMember(recipientId);
        }, onGroupsLoaded::accept);
    }

    public void getMemberCount(@NonNull Consumer<Integer> onMemberCountLoaded) {
        SimpleTask.run(() -> {
            GroupDatabase                       groupDatabase = DatabaseFactory.getGroupDatabase(context);
            Optional<GroupDatabase.GroupRecord> groupRecord   = groupDatabase.getGroup(recipientId);
            return groupRecord.transform(record -> record.getMembers().size()).or(0);
        }, onMemberCountLoaded::accept);
    }

    public void acceptMessageRequest(@NonNull Runnable onMessageRequestAccepted) {
        SimpleTask.run(() -> {
            RecipientDatabase recipientDatabase = DatabaseFactory.getRecipientDatabase(context);
            recipientDatabase.setProfileSharing(recipientId, true);
            liveRecipient.refresh();

            List<MessagingDatabase.MarkedMessageInfo> messageIds = DatabaseFactory.getThreadDatabase(context)
                    .setEntireThreadRead(threadId);
            MessageNotifier.updateNotification(context);
            MarkReadReceiver.process(context, messageIds);

            return null;
        }, v -> onMessageRequestAccepted.run());
    }

    public void deleteMessageRequest(@NonNull Runnable onMessageRequestDeleted) {
        SimpleTask.run(() -> {
            ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(context);
            threadDatabase.deleteConversation(threadId);
            return null;
        }, v -> onMessageRequestDeleted.run());
    }

    public void blockMessageRequest(@NonNull Runnable onMessageRequestBlocked) {
        SimpleTask.run(() -> {
            Recipient recipient = liveRecipient.resolve();
            RecipientUtil.block(context, recipient);
            liveRecipient.refresh();
            return null;
        }, v -> onMessageRequestBlocked.run());
    }
}