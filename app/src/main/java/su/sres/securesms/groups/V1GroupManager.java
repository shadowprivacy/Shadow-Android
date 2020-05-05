package su.sres.securesms.groups;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.google.protobuf.ByteString;

import su.sres.securesms.attachments.Attachment;
import su.sres.securesms.attachments.UriAttachment;
import su.sres.securesms.database.AttachmentDatabase;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.GroupDatabase;
import su.sres.securesms.database.ThreadDatabase;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.groups.GroupManager.GroupActionResult;
import su.sres.securesms.jobs.LeaveGroupJob;
import su.sres.securesms.mms.OutgoingGroupMediaMessage;
import su.sres.securesms.providers.BlobProvider;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.recipients.RecipientUtil;
import su.sres.securesms.sms.MessageSender;
import su.sres.securesms.util.BitmapUtil;
import su.sres.securesms.util.GroupUtil;
import su.sres.securesms.util.MediaUtil;
import org.whispersystems.libsignal.util.guava.Optional;
import su.sres.signalservice.api.util.InvalidNumberException;
import su.sres.signalservice.internal.push.SignalServiceProtos.GroupContext;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

final class V1GroupManager {

    static @NonNull GroupActionResult createGroup(@NonNull Context          context,
                                                  @NonNull Set<RecipientId> memberIds,
                                                  @Nullable Bitmap          avatar,
                                                  @Nullable String          name,
                                                  boolean         mms)
    {
        final byte[]        avatarBytes      = BitmapUtil.toByteArray(avatar);
        final GroupDatabase groupDatabase    = DatabaseFactory.getGroupDatabase(context);
        final GroupId       groupId          = GroupDatabase.allocateGroupId(mms);
        final RecipientId   groupRecipientId = DatabaseFactory.getRecipientDatabase(context).getOrInsertFromGroupId(groupId);
        final Recipient     groupRecipient   = Recipient.resolved(groupRecipientId);

        memberIds.add(Recipient.self().getId());
        groupDatabase.create(groupId, name, new LinkedList<>(memberIds), null, null);

        if (!mms) {
            groupDatabase.updateAvatar(groupId, avatarBytes);
            DatabaseFactory.getRecipientDatabase(context).setProfileSharing(groupRecipient.getId(), true);
            return sendGroupUpdate(context, groupId, memberIds, name, avatarBytes);
        } else {
            long threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(groupRecipient, ThreadDatabase.DistributionTypes.CONVERSATION);
            return new GroupActionResult(groupRecipient, threadId);
        }
    }

    static GroupActionResult updateGroup(@NonNull  Context          context,
                                         @NonNull  GroupId          groupId,
                                         @NonNull  Set<RecipientId> memberAddresses,
                                         @Nullable Bitmap           avatar,
                                         @Nullable String           name)
            throws InvalidNumberException
    {
        final GroupDatabase groupDatabase = DatabaseFactory.getGroupDatabase(context);
        final byte[]        avatarBytes   = BitmapUtil.toByteArray(avatar);

        memberAddresses.add(Recipient.self().getId());
        groupDatabase.updateMembers(groupId, new LinkedList<>(memberAddresses));
        groupDatabase.updateTitle(groupId, name);
        groupDatabase.updateAvatar(groupId, avatarBytes);

        if (!groupId.isMmsGroup()) {
            return sendGroupUpdate(context, groupId, memberAddresses, name, avatarBytes);
        } else {
            RecipientId groupRecipientId = DatabaseFactory.getRecipientDatabase(context).getOrInsertFromGroupId(groupId);
            Recipient   groupRecipient   = Recipient.resolved(groupRecipientId);
            long        threadId         = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(groupRecipient);
            return new GroupActionResult(groupRecipient, threadId);
        }
    }

    private static GroupActionResult sendGroupUpdate(@NonNull  Context          context,
                                                     @NonNull  GroupId          groupId,
                                                     @NonNull  Set<RecipientId> members,
                                                     @Nullable String           groupName,
                                                     @Nullable byte[]           avatar)
    {
        Attachment  avatarAttachment = null;
        RecipientId groupRecipientId = DatabaseFactory.getRecipientDatabase(context).getOrInsertFromGroupId(groupId);
        Recipient   groupRecipient   = Recipient.resolved(groupRecipientId);

        List<GroupContext.Member> uuidMembers = new LinkedList<>();
        List<String>              e164Members = new LinkedList<>();

        for (RecipientId member : members) {
            Recipient recipient = Recipient.resolved(member);
            uuidMembers.add(GroupMessageProcessor.createMember(RecipientUtil.toSignalServiceAddress(context, recipient)));
        }

        GroupContext.Builder groupContextBuilder = GroupContext.newBuilder()
                .setId(ByteString.copyFrom(groupId.getDecodedId()))
                .setType(GroupContext.Type.UPDATE)
                .addAllMembersE164(e164Members)
                .addAllMembers(uuidMembers);
        if (groupName != null) groupContextBuilder.setName(groupName);
        GroupContext groupContext = groupContextBuilder.build();

        if (avatar != null) {
            Uri avatarUri = BlobProvider.getInstance().forData(avatar).createForSingleUseInMemory();
            avatarAttachment = new UriAttachment(avatarUri, MediaUtil.IMAGE_PNG, AttachmentDatabase.TRANSFER_PROGRESS_DONE, avatar.length, null, false, false, null, null, null, null);
        }

        OutgoingGroupMediaMessage outgoingMessage = new OutgoingGroupMediaMessage(groupRecipient, groupContext, avatarAttachment, System.currentTimeMillis(), 0, false, null, Collections.emptyList(), Collections.emptyList());
        long                      threadId        = MessageSender.send(context, outgoingMessage, -1, false, null);

        return new GroupActionResult(groupRecipient, threadId);
    }

    @WorkerThread
    static boolean leaveGroup(@NonNull Context context, @NonNull GroupId groupId, @NonNull Recipient groupRecipient) {
        long                                threadId     = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(groupRecipient);
        Optional<OutgoingGroupMediaMessage> leaveMessage = GroupUtil.createGroupLeaveMessage(context, groupRecipient);

        if (threadId != -1 && leaveMessage.isPresent()) {
            ApplicationDependencies.getJobManager().add(LeaveGroupJob.create(groupRecipient));

            GroupDatabase groupDatabase = DatabaseFactory.getGroupDatabase(context);
            groupDatabase.setActive(groupId, false);
            groupDatabase.remove(groupId, Recipient.self().getId());
            return true;
        } else {
            return false;
        }
    }
}