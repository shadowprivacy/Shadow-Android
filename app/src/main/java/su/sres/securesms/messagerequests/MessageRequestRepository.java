package su.sres.securesms.messagerequests;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;

import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.GroupDatabase;
import su.sres.securesms.database.MessagingDatabase;
import su.sres.securesms.database.RecipientDatabase;
import su.sres.securesms.database.ThreadDatabase;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.groups.GroupChangeBusyException;
import su.sres.securesms.groups.GroupChangeFailedException;
import su.sres.securesms.groups.GroupInsufficientRightsException;
import su.sres.securesms.groups.GroupManager;
import su.sres.securesms.groups.GroupNotAMemberException;
import su.sres.securesms.groups.ui.GroupChangeErrorCallback;
import su.sres.securesms.groups.ui.GroupChangeFailureReason;
import su.sres.securesms.jobs.MultiDeviceMessageRequestResponseJob;
import su.sres.securesms.logging.Log;
import su.sres.securesms.notifications.MarkReadReceiver;
import su.sres.securesms.notifications.MessageNotifier;
import su.sres.securesms.recipients.LiveRecipient;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.recipients.RecipientUtil;
import su.sres.securesms.sms.MessageSender;
import su.sres.securesms.util.TextSecurePreferences;
import su.sres.securesms.util.Util;
import su.sres.securesms.util.concurrent.SignalExecutors;
import su.sres.storageservice.protos.groups.local.DecryptedGroup;

import org.whispersystems.libsignal.util.guava.Optional;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;

final class MessageRequestRepository {

    private static final String TAG = Log.tag(MessageRequestRepository.class);

    private final Context  context;
    private final Executor executor;

    MessageRequestRepository(@NonNull Context context) {
        this.context  = context.getApplicationContext();
        this.executor = SignalExecutors.BOUNDED;
    }

    void getGroups(@NonNull RecipientId recipientId, @NonNull Consumer<List<String>> onGroupsLoaded) {
        executor.execute(() -> {
            GroupDatabase groupDatabase = DatabaseFactory.getGroupDatabase(context);
            onGroupsLoaded.accept(groupDatabase.getPushGroupNamesContainingMember(recipientId));
        });
    }

    void getMemberCount(@NonNull RecipientId recipientId, @NonNull Consumer<GroupMemberCount> onMemberCountLoaded) {
        executor.execute(() -> {
            GroupDatabase groupDatabase = DatabaseFactory.getGroupDatabase(context);
            Optional<GroupDatabase.GroupRecord> groupRecord = groupDatabase.getGroup(recipientId);
            onMemberCountLoaded.accept(groupRecord.transform(record -> {
                if (record.isV2Group()) {
                    DecryptedGroup decryptedGroup = record.requireV2GroupProperties().getDecryptedGroup();
                    return new GroupMemberCount(decryptedGroup.getMembersCount(), decryptedGroup.getPendingMembersCount());
                } else {
                    return new GroupMemberCount(record.getMembers().size(), 0);
                }
            }).or(GroupMemberCount.ZERO));
        });
    }

    void getMessageRequestState(@NonNull Recipient recipient, long threadId, @NonNull Consumer<MessageRequestState> state) {
        executor.execute(() -> {
            if (recipient.isPushV2Group()) {
                boolean pendingMember = DatabaseFactory.getGroupDatabase(context)
                        .isPendingMember(recipient.requireGroupId().requireV2(), Recipient.self());
                state.accept(pendingMember ? MessageRequestState.UNACCEPTED
                        : MessageRequestState.ACCEPTED);
            } else if (!RecipientUtil.isMessageRequestAccepted(context, threadId)) {
                state.accept(MessageRequestState.UNACCEPTED);
            } else if (RecipientUtil.isPreMessageRequestThread(context, threadId) && !RecipientUtil.isLegacyProfileSharingAccepted(recipient)) {
                state.accept(MessageRequestState.LEGACY);
            } else {
                state.accept(MessageRequestState.ACCEPTED);
            }
        });
    }

    void acceptMessageRequest(@NonNull LiveRecipient liveRecipient,
                              long threadId,
                              @NonNull Runnable onMessageRequestAccepted,
                              @NonNull GroupChangeErrorCallback mainThreadError)
    {
        GroupChangeErrorCallback error = e -> Util.runOnMain(() -> mainThreadError.onError(e));
        executor.execute(()-> {
            if (liveRecipient.get().isPushV2Group()) {
                try {
                    Log.i(TAG, "GV2 accepting invite");
                    GroupManager.acceptInvite(context, liveRecipient.get().requireGroupId().requireV2());

                    RecipientDatabase recipientDatabase = DatabaseFactory.getRecipientDatabase(context);
                    recipientDatabase.setProfileSharing(liveRecipient.getId(), true);

                    onMessageRequestAccepted.run();
                } catch (GroupInsufficientRightsException e) {
                    Log.w(TAG, e);
                    error.onError(GroupChangeFailureReason.NO_RIGHTS);
                } catch (GroupChangeBusyException | GroupChangeFailedException | GroupNotAMemberException | IOException e) {
                    Log.w(TAG, e);
                    error.onError(GroupChangeFailureReason.OTHER);
                }
            } else {
                RecipientDatabase recipientDatabase = DatabaseFactory.getRecipientDatabase(context);
                recipientDatabase.setProfileSharing(liveRecipient.getId(), true);

                MessageSender.sendProfileKey(context, threadId);

                List<MessagingDatabase.MarkedMessageInfo> messageIds = DatabaseFactory.getThreadDatabase(context)
                        .setEntireThreadRead(threadId);
                MessageNotifier.updateNotification(context);
                MarkReadReceiver.process(context, messageIds);

                if (TextSecurePreferences.isMultiDevice(context)) {
                    ApplicationDependencies.getJobManager().add(MultiDeviceMessageRequestResponseJob.forAccept(liveRecipient.getId()));
                }

                onMessageRequestAccepted.run();
            }
        });
    }

    void deleteMessageRequest(@NonNull LiveRecipient recipient, long threadId, @NonNull Runnable onMessageRequestDeleted) {
        executor.execute(() -> {
            ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(context);
            threadDatabase.deleteConversation(threadId);

            if (recipient.resolve().isGroup()) {
                RecipientUtil.leaveGroup(context, recipient.get());
            }

            if (TextSecurePreferences.isMultiDevice(context)) {
                ApplicationDependencies.getJobManager().add(MultiDeviceMessageRequestResponseJob.forDelete(recipient.getId()));
            }

            onMessageRequestDeleted.run();
        });
    }

    void blockMessageRequest(@NonNull LiveRecipient liveRecipient, @NonNull Runnable onMessageRequestBlocked) {
        executor.execute(() -> {
            Recipient recipient = liveRecipient.resolve();
            RecipientUtil.block(context, recipient);
            liveRecipient.refresh();

            if (TextSecurePreferences.isMultiDevice(context)) {
                ApplicationDependencies.getJobManager().add(MultiDeviceMessageRequestResponseJob.forBlock(liveRecipient.getId()));
            }

            onMessageRequestBlocked.run();
        });
    }

    void blockAndDeleteMessageRequest(@NonNull LiveRecipient liveRecipient, long threadId, @NonNull Runnable onMessageRequestBlocked) {
        executor.execute(() -> {
            Recipient recipient = liveRecipient.resolve();
            RecipientUtil.block(context, recipient);
            liveRecipient.refresh();

            DatabaseFactory.getThreadDatabase(context).deleteConversation(threadId);

            if (TextSecurePreferences.isMultiDevice(context)) {
                ApplicationDependencies.getJobManager().add(MultiDeviceMessageRequestResponseJob.forBlockAndDelete(liveRecipient.getId()));
            }

            onMessageRequestBlocked.run();
        });
    }

    void unblockAndAccept(@NonNull LiveRecipient liveRecipient, long threadId, @NonNull Runnable onMessageRequestUnblocked) {
        executor.execute(() -> {
            Recipient         recipient         = liveRecipient.resolve();
            RecipientDatabase recipientDatabase = DatabaseFactory.getRecipientDatabase(context);

            RecipientUtil.unblock(context, recipient);
            recipientDatabase.setProfileSharing(liveRecipient.getId(), true);
            liveRecipient.refresh();

            List<MessagingDatabase.MarkedMessageInfo> messageIds = DatabaseFactory.getThreadDatabase(context)
                    .setEntireThreadRead(threadId);
            MessageNotifier.updateNotification(context);
            MarkReadReceiver.process(context, messageIds);

            if (TextSecurePreferences.isMultiDevice(context)) {
                ApplicationDependencies.getJobManager().add(MultiDeviceMessageRequestResponseJob.forAccept(liveRecipient.getId()));
            }

            onMessageRequestUnblocked.run();
        });
    }

    enum MessageRequestState {
        ACCEPTED, UNACCEPTED, LEGACY
    }
}