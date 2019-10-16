package su.sres.securesms.groups;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.protobuf.ByteString;

import su.sres.securesms.ApplicationContext;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.GroupDatabase;
import su.sres.securesms.database.MessagingDatabase.InsertResult;
import su.sres.securesms.database.MmsDatabase;
import su.sres.securesms.database.SmsDatabase;
import su.sres.securesms.jobs.AvatarDownloadJob;
import su.sres.securesms.jobs.PushGroupUpdateJob;
import su.sres.securesms.logging.Log;
import su.sres.securesms.mms.MmsException;
import su.sres.securesms.mms.OutgoingGroupMediaMessage;
import su.sres.securesms.notifications.MessageNotifier;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.sms.IncomingGroupMessage;
import su.sres.securesms.sms.IncomingTextMessage;
import su.sres.securesms.util.Base64;
import su.sres.securesms.util.GroupUtil;
import org.whispersystems.libsignal.util.guava.Optional;
import su.sres.signalservice.api.messages.SignalServiceAttachment;
import su.sres.signalservice.api.messages.SignalServiceContent;
import su.sres.signalservice.api.messages.SignalServiceDataMessage;
import su.sres.signalservice.api.messages.SignalServiceGroup;
import su.sres.signalservice.api.messages.SignalServiceGroup.Type;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static su.sres.securesms.database.GroupDatabase.GroupRecord;
import static su.sres.signalservice.internal.push.SignalServiceProtos.AttachmentPointer;
import static su.sres.signalservice.internal.push.SignalServiceProtos.GroupContext;

public class GroupMessageProcessor {

  private static final String TAG = GroupMessageProcessor.class.getSimpleName();

  public static @Nullable Long process(@NonNull Context context,
                                       @NonNull SignalServiceContent content,
                                       @NonNull SignalServiceDataMessage message,
                                       boolean outgoing)
  {
    if (!message.getGroupInfo().isPresent() || message.getGroupInfo().get().getGroupId() == null) {
      Log.w(TAG, "Received group message with no id! Ignoring...");
      return null;
    }

    GroupDatabase         database = DatabaseFactory.getGroupDatabase(context);
    SignalServiceGroup    group    = message.getGroupInfo().get();
    String                id       = GroupUtil.getEncodedId(group.getGroupId(), false);
    Optional<GroupRecord> record   = database.getGroup(id);

    if (record.isPresent() && group.getType() == Type.UPDATE) {
      return handleGroupUpdate(context, content, group, record.get(), outgoing);
    } else if (!record.isPresent() && group.getType() == Type.UPDATE) {
      return handleGroupCreate(context, content, group, outgoing);
    } else if (record.isPresent() && group.getType() == Type.QUIT) {
      return handleGroupLeave(context, content, group, record.get(), outgoing);
    } else if (record.isPresent() && group.getType() == Type.REQUEST_INFO) {
      return handleGroupInfoRequest(context, content, group, record.get());
    } else {
      Log.w(TAG, "Received unknown type, ignoring...");
      return null;
    }
  }

  private static @Nullable Long handleGroupCreate(@NonNull Context context,
                                                  @NonNull SignalServiceContent content,
                                                  @NonNull SignalServiceGroup group,
                                                  boolean outgoing)
  {
    GroupDatabase        database = DatabaseFactory.getGroupDatabase(context);
    String               id       = GroupUtil.getEncodedId(group.getGroupId(), false);
    GroupContext.Builder builder  = createGroupContext(group);
    builder.setType(GroupContext.Type.UPDATE);

    SignalServiceAttachment avatar  = group.getAvatar().orNull();
    List<RecipientId>       members = new LinkedList<>();

    if (group.getMembers().isPresent()) {
      for (String member : group.getMembers().get()) {
        members.add(Recipient.external(context, member).getId());
      }
    }

    database.create(id, group.getName().orNull(), members,
            avatar != null && avatar.isPointer() ? avatar.asPointer() : null, null);

    return storeMessage(context, content, group, builder.build(), outgoing);
  }

  private static @Nullable Long handleGroupUpdate(@NonNull Context context,
                                                  @NonNull SignalServiceContent content,
                                                  @NonNull SignalServiceGroup group,
                                                  @NonNull GroupRecord groupRecord,
                                                  boolean outgoing)
  {

    GroupDatabase database = DatabaseFactory.getGroupDatabase(context);
    String        id       = GroupUtil.getEncodedId(group.getGroupId(), false);

    Set<RecipientId> recordMembers  = new HashSet<>(groupRecord.getMembers());
    Set<RecipientId> messageMembers = new HashSet<>();

    for (String messageMember : group.getMembers().get()) {
      messageMembers.add(Recipient.external(context, messageMember).getId());
    }

    Set<RecipientId> addedMembers = new HashSet<>(messageMembers);
    addedMembers.removeAll(recordMembers);

    Set<RecipientId> missingMembers = new HashSet<>(recordMembers);
    missingMembers.removeAll(messageMembers);

    GroupContext.Builder builder = createGroupContext(group);
    builder.setType(GroupContext.Type.UPDATE);

    if (addedMembers.size() > 0) {
      Set<RecipientId> unionMembers = new HashSet<>(recordMembers);
      unionMembers.addAll(messageMembers);
      database.updateMembers(id, new LinkedList<>(unionMembers));

      builder.clearMembers();

      for (RecipientId addedMember : addedMembers) {
        builder.addMembers(Recipient.resolved(addedMember).requireAddress().serialize());
      }
    } else {
      builder.clearMembers();
    }

    if (missingMembers.size() > 0) {
      // TODO We should tell added and missing about each-other.
    }

    if (group.getName().isPresent() || group.getAvatar().isPresent()) {
      SignalServiceAttachment avatar = group.getAvatar().orNull();
      database.update(id, group.getName().orNull(), avatar != null ? avatar.asPointer() : null);
    }

    if (group.getName().isPresent() && group.getName().get().equals(groupRecord.getTitle())) {
      builder.clearName();
    }

    if (!groupRecord.isActive()) database.setActive(id, true);

    return storeMessage(context, content, group, builder.build(), outgoing);
  }

  private static Long handleGroupInfoRequest(@NonNull Context context,
                                             @NonNull SignalServiceContent content,
                                             @NonNull SignalServiceGroup group,
                                             @NonNull GroupRecord record)
  {
    Recipient sender = Recipient.external(context, content.getSender());

    if (record.getMembers().contains(sender.getId())) {
      ApplicationContext.getInstance(context)
                        .getJobManager()
              .add(new PushGroupUpdateJob(sender.getId(), group.getGroupId()));
    }

    return null;
  }

  private static Long handleGroupLeave(@NonNull Context               context,
                                       @NonNull SignalServiceContent  content,
                                       @NonNull SignalServiceGroup    group,
                                       @NonNull GroupRecord           record,
                                       boolean  outgoing)
  {
    GroupDatabase     database = DatabaseFactory.getGroupDatabase(context);
    String            id       = GroupUtil.getEncodedId(group.getGroupId(), false);
    List<RecipientId> members  = record.getMembers();

    GroupContext.Builder builder = createGroupContext(group);
    builder.setType(GroupContext.Type.QUIT);

    if (members.contains(Recipient.external(context, content.getSender()).getId())) {
      database.remove(id, Recipient.external(context, content.getSender()).getId());
      if (outgoing) database.setActive(id, false);

      return storeMessage(context, content, group, builder.build(), outgoing);
    }

    return null;
  }


  private static @Nullable Long storeMessage(@NonNull Context context,
                                             @NonNull SignalServiceContent content,
                                             @NonNull SignalServiceGroup group,
                                             @NonNull GroupContext storage,
                                             boolean  outgoing)
  {
    if (group.getAvatar().isPresent()) {
      ApplicationContext.getInstance(context).getJobManager()
              .add(new AvatarDownloadJob(group.getGroupId()));
    }

    try {
      if (outgoing) {
        MmsDatabase               mmsDatabase     = DatabaseFactory.getMmsDatabase(context);
        RecipientId               recipientId     = DatabaseFactory.getRecipientDatabase(context).getOrInsertFromGroupId(GroupUtil.getEncodedId(group.getGroupId(), false));
        Recipient                 recipient       = Recipient.resolved(recipientId);
        OutgoingGroupMediaMessage outgoingMessage = new OutgoingGroupMediaMessage(recipient, storage, null, content.getTimestamp(), 0, false, null, Collections.emptyList(), Collections.emptyList());
        long                      threadId        = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient);
        long                      messageId       = mmsDatabase.insertMessageOutbox(outgoingMessage, threadId, false, null);

        mmsDatabase.markAsSent(messageId, true);

        return threadId;
      } else {
        SmsDatabase          smsDatabase  = DatabaseFactory.getSmsDatabase(context);
        String               body         = Base64.encodeBytes(storage.toByteArray());
        IncomingTextMessage  incoming     = new IncomingTextMessage(Recipient.external(context, content.getSender()).getId(), content.getSenderDevice(), content.getTimestamp(), body, Optional.of(group), 0, content.isNeedsReceipt());
        IncomingGroupMessage groupMessage = new IncomingGroupMessage(incoming, storage, body);

        Optional<InsertResult> insertResult = smsDatabase.insertMessageInbox(groupMessage);

        if (insertResult.isPresent()) {
          MessageNotifier.updateNotification(context, insertResult.get().getThreadId());
          return insertResult.get().getThreadId();
        } else {
          return null;
        }
      }
    } catch (MmsException e) {
      Log.w(TAG, e);
    }

    return null;
  }

  private static GroupContext.Builder createGroupContext(SignalServiceGroup group) {
    GroupContext.Builder builder = GroupContext.newBuilder();
    builder.setId(ByteString.copyFrom(group.getGroupId()));

    if (group.getAvatar().isPresent() && group.getAvatar().get().isPointer()) {
      builder.setAvatar(AttachmentPointer.newBuilder()
                                         .setId(group.getAvatar().get().asPointer().getId())
                                         .setKey(ByteString.copyFrom(group.getAvatar().get().asPointer().getKey()))
                                         .setContentType(group.getAvatar().get().getContentType()));
    }

    if (group.getName().isPresent()) {
      builder.setName(group.getName().get());
    }

    if (group.getMembers().isPresent()) {
      builder.addAllMembers(group.getMembers().get());
    }

    return builder;
  }

}
