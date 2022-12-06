package su.sres.securesms.messagerequests;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.core.util.Consumer;

import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.GroupDatabase;
import su.sres.securesms.database.MessageDatabase;
import su.sres.securesms.database.RecipientDatabase;
import su.sres.securesms.database.ThreadDatabase;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.groups.GroupChangeException;
import su.sres.securesms.groups.GroupId;
import su.sres.securesms.groups.GroupManager;
import su.sres.securesms.groups.ui.GroupChangeErrorCallback;
import su.sres.securesms.groups.ui.GroupChangeFailureReason;
import su.sres.securesms.jobs.MultiDeviceMessageRequestResponseJob;
import su.sres.securesms.logging.Log;
import su.sres.securesms.notifications.MarkReadReceiver;
import su.sres.securesms.recipients.LiveRecipient;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.recipients.RecipientUtil;
import su.sres.securesms.sms.MessageSender;
import su.sres.securesms.util.FeatureFlags;
import su.sres.securesms.util.TextSecurePreferences;
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
            GroupDatabase                       groupDatabase = DatabaseFactory.getGroupDatabase(context);
            Optional<GroupDatabase.GroupRecord> groupRecord   = groupDatabase.getGroup(recipientId);
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
        executor.execute(() -> state.accept(findMessageRequestState(recipient, threadId)));
    }

    @WorkerThread
    private MessageRequestState findMessageRequestState(@NonNull Recipient recipient, long threadId) {
        if (!RecipientUtil.isMessageRequestAccepted(context, threadId)) {
            if (recipient.isGroup()) {
                GroupDatabase.MemberLevel memberLevel = DatabaseFactory.getGroupDatabase(context)
                        .getGroup(recipient.getId())
                        .transform(g -> g.memberLevel(Recipient.self()))
                        .or(GroupDatabase.MemberLevel.NOT_A_MEMBER);

                if (memberLevel == GroupDatabase.MemberLevel.NOT_A_MEMBER) {
                    return MessageRequestState.NOT_REQUIRED;
                }
            }

            return MessageRequestState.REQUIRED;
        } else if (FeatureFlags.modernProfileSharing() && !RecipientUtil.isLegacyProfileSharingAccepted(recipient)) {
            return MessageRequestState.REQUIRED;
        } else if (RecipientUtil.isPreMessageRequestThread(context, threadId) && !RecipientUtil.isLegacyProfileSharingAccepted(recipient)) {
            return MessageRequestState.PRE_MESSAGE_REQUEST;
        } else {
            return MessageRequestState.NOT_REQUIRED;
        }
    }

    void acceptMessageRequest(@NonNull LiveRecipient liveRecipient,
                              long threadId,
                              @NonNull Runnable onMessageRequestAccepted,
                              @NonNull GroupChangeErrorCallback error)
    {
        executor.execute(()-> {
            if (liveRecipient.get().isPushV2Group()) {
                try {
                    Log.i(TAG, "GV2 accepting invite");
                    GroupManager.acceptInvite(context, liveRecipient.get().requireGroupId().requireV2());

                    RecipientDatabase recipientDatabase = DatabaseFactory.getRecipientDatabase(context);
                    recipientDatabase.setProfileSharing(liveRecipient.getId(), true);

                    onMessageRequestAccepted.run();
                } catch (GroupChangeException | IOException e) {
                    Log.w(TAG, e);
                    error.onError(GroupChangeFailureReason.fromException(e));
                }
            } else {
                RecipientDatabase recipientDatabase = DatabaseFactory.getRecipientDatabase(context);
                recipientDatabase.setProfileSharing(liveRecipient.getId(), true);

                MessageSender.sendProfileKey(context, threadId);

                List<MessageDatabase.MarkedMessageInfo> messageIds = DatabaseFactory.getThreadDatabase(context)
                        .setEntireThreadRead(threadId);
                ApplicationDependencies.getMessageNotifier().updateNotification(context);
                MarkReadReceiver.process(context, messageIds);

                if (TextSecurePreferences.isMultiDevice(context)) {
                    ApplicationDependencies.getJobManager().add(MultiDeviceMessageRequestResponseJob.forAccept(liveRecipient.getId()));
                }

                onMessageRequestAccepted.run();
            }
        });
    }

    void deleteMessageRequest(@NonNull LiveRecipient recipient,
                              long threadId,
                              @NonNull Runnable onMessageRequestDeleted,
                              @NonNull GroupChangeErrorCallback error)
    {
        executor.execute(() -> {
            Recipient resolved = recipient.resolve();

            if (resolved.isGroup() && resolved.requireGroupId().isPush()) {
                try {
                    GroupManager.leaveGroupFromBlockOrMessageRequest(context, resolved.requireGroupId().requirePush());
                } catch (GroupChangeException | IOException e) {
                    Log.w(TAG, e);
                    error.onError(GroupChangeFailureReason.fromException(e));
                    return;
                }
            }

            if (TextSecurePreferences.isMultiDevice(context)) {
                ApplicationDependencies.getJobManager().add(MultiDeviceMessageRequestResponseJob.forDelete(recipient.getId()));
            }

            ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(context);
            threadDatabase.deleteConversation(threadId);

            onMessageRequestDeleted.run();
        });
    }

    void blockMessageRequest(@NonNull LiveRecipient liveRecipient,
                             @NonNull Runnable onMessageRequestBlocked,
                             @NonNull GroupChangeErrorCallback error)
    {
        executor.execute(() -> {
            Recipient recipient = liveRecipient.resolve();
            try {
                RecipientUtil.block(context, recipient);
            } catch (GroupChangeException | IOException e) {
                Log.w(TAG, e);
                error.onError(GroupChangeFailureReason.fromException(e));
                return;
            }
            liveRecipient.refresh();

            if (TextSecurePreferences.isMultiDevice(context)) {
                ApplicationDependencies.getJobManager().add(MultiDeviceMessageRequestResponseJob.forBlock(liveRecipient.getId()));
            }

            onMessageRequestBlocked.run();
        });
    }

    void blockAndDeleteMessageRequest(@NonNull LiveRecipient liveRecipient,
                                      long threadId,
                                      @NonNull Runnable onMessageRequestBlocked,
                                      @NonNull GroupChangeErrorCallback error)
    {
        executor.execute(() -> {
            Recipient recipient = liveRecipient.resolve();
            try{
                RecipientUtil.block(context, recipient);
            } catch (GroupChangeException | IOException e) {
                Log.w(TAG, e);
                error.onError(GroupChangeFailureReason.fromException(e));
                return;
            }
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
            Recipient recipient = liveRecipient.resolve();

            RecipientUtil.unblock(context, recipient);

            List<MessageDatabase.MarkedMessageInfo> messageIds = DatabaseFactory.getThreadDatabase(context)
                    .setEntireThreadRead(threadId);
            ApplicationDependencies.getMessageNotifier().updateNotification(context);
            MarkReadReceiver.process(context, messageIds);

            if (TextSecurePreferences.isMultiDevice(context)) {
                ApplicationDependencies.getJobManager().add(MultiDeviceMessageRequestResponseJob.forAccept(liveRecipient.getId()));
            }

            onMessageRequestUnblocked.run();
        });
    }

    @WorkerThread
    boolean isPendingMember(@NonNull GroupId.V2 groupId) {
        return DatabaseFactory.getGroupDatabase(context).isPendingMember(groupId, Recipient.self());
    }

    enum MessageRequestState {
        /**
         * Message request permission does not need to be gained at this time.
         * <p>
         * Either:
         * - Explicit message request has been accepted, or;
         * - Did not need to be shown because they are a contact etc, or;
         * - It's a group that they are no longer in or invited to.
         */
        NOT_REQUIRED,

        /** Explicit message request permission is required. */
        REQUIRED,

        /** This conversation existed before message requests and needs the old UI */
        PRE_MESSAGE_REQUEST
    }
}