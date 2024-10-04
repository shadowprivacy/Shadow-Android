/*
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package su.sres.securesms.sms;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Stream;

import su.sres.securesms.attachments.AttachmentId;
import su.sres.securesms.attachments.DatabaseAttachment;
import su.sres.securesms.contactshare.Contact;
import su.sres.securesms.database.MessageDatabase.SyncMessageId;
import su.sres.securesms.database.MmsSmsDatabase;
import su.sres.securesms.database.NoSuchMessageException;
import su.sres.securesms.database.model.SmsMessageRecord;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobmanager.JobManager;
import su.sres.securesms.jobs.AttachmentCopyJob;
import su.sres.securesms.jobs.AttachmentUploadJob;
import su.sres.securesms.jobs.ProfileKeySendJob;
import su.sres.securesms.jobs.RemoteDeleteSendJob;
import su.sres.securesms.jobs.ResumableUploadSpecJob;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.securesms.linkpreview.LinkPreview;
import su.sres.core.util.logging.Log;

import su.sres.securesms.attachments.Attachment;
import su.sres.securesms.database.AttachmentDatabase;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.MessageDatabase;
import su.sres.securesms.database.RecipientDatabase;
import su.sres.securesms.database.SmsDatabase;
import su.sres.securesms.database.ThreadDatabase;
import su.sres.securesms.database.model.MessageRecord;
import su.sres.securesms.database.model.ReactionRecord;
import su.sres.securesms.jobs.AttachmentCompressionJob;
import su.sres.securesms.jobs.AttachmentMarkUploadedJob;
import su.sres.securesms.jobs.MmsSendJob;
import su.sres.securesms.jobs.PushGroupSendJob;
import su.sres.securesms.jobs.PushMediaSendJob;
import su.sres.securesms.jobs.PushTextSendJob;
import su.sres.securesms.jobs.ReactionSendJob;
import su.sres.securesms.jobs.SmsSendJob;
import su.sres.securesms.mms.MmsException;
import su.sres.securesms.mms.OutgoingMediaMessage;
import su.sres.securesms.mms.OutgoingSecureMediaMessage;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.recipients.RecipientUtil;
import su.sres.securesms.service.ExpiringMessageManager;
import su.sres.securesms.util.ParcelUtil;
import su.sres.securesms.util.ShadowLocalMetrics;
import su.sres.securesms.util.TextSecurePreferences;

import org.greenrobot.eventbus.EventBus;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.libsignal.util.guava.Preconditions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MessageSender {

  private static final String TAG = Log.tag(MessageSender.class);

  /**
   * Suitable for a 1:1 conversation or a GV1 group only.
   */
  @WorkerThread
  public static void sendProfileKey(final Context context, final long threadId) {
    ApplicationDependencies.getJobManager().add(ProfileKeySendJob.create(context, threadId, false));
  }

  public static long send(final Context context,
                          final OutgoingTextMessage message,
                          final long threadId,
                          final boolean forceSms,
                          @Nullable final String metricId,
                          final SmsDatabase.InsertListener insertListener)
  {
    Log.i(TAG, "Sending text message to " + message.getRecipient().getId() + ", thread: " + threadId);
    MessageDatabase database    = DatabaseFactory.getSmsDatabase(context);
    Recipient       recipient   = message.getRecipient();
    boolean         keyExchange = message.isKeyExchange();

    long allocatedThreadId = DatabaseFactory.getThreadDatabase(context).getOrCreateValidThreadId(recipient, threadId);
    long messageId = database.insertMessageOutbox(allocatedThreadId,
                                                  applyUniversalExpireTimerIfNecessary(context, recipient, message, allocatedThreadId),
                                                  forceSms,
                                                  System.currentTimeMillis(),
                                                  insertListener);

    ShadowLocalMetrics.IndividualMessageSend.onInsertedIntoDatabase(messageId, metricId);

    sendTextMessage(context, recipient, forceSms, keyExchange, messageId);
    onMessageSent();
    DatabaseFactory.getThreadDatabase(context).update(threadId, true);

    return allocatedThreadId;
  }

  public static long send(final Context context,
                          final OutgoingMediaMessage message,
                          final long threadId,
                          final boolean forceSms,
                          @Nullable final String metricId,
                          final SmsDatabase.InsertListener insertListener)
  {
    Log.i(TAG, "Sending media message to " + message.getRecipient().getId() + ", thread: " + threadId);
    try {
      ThreadDatabase  threadDatabase = DatabaseFactory.getThreadDatabase(context);
      MessageDatabase database       = DatabaseFactory.getMmsDatabase(context);

      long      allocatedThreadId = threadDatabase.getOrCreateValidThreadId(message.getRecipient(), threadId, message.getDistributionType());
      Recipient recipient         = message.getRecipient();
      long      messageId         = database.insertMessageOutbox(applyUniversalExpireTimerIfNecessary(context, recipient, message, allocatedThreadId), allocatedThreadId, forceSms, insertListener);

      if (message.getRecipient().isGroup() && message.getAttachments().isEmpty() && message.getLinkPreviews().isEmpty() && message.getSharedContacts().isEmpty()) {
        ShadowLocalMetrics.GroupMessageSend.onInsertedIntoDatabase(messageId, metricId);
      } else {
        ShadowLocalMetrics.GroupMessageSend.cancel(metricId);
      }

      sendMediaMessage(context, recipient, forceSms, messageId, Collections.emptyList());
      onMessageSent();
      threadDatabase.update(threadId, true);

      return allocatedThreadId;
    } catch (MmsException e) {
      Log.w(TAG, e);
      return threadId;
    }
  }

  public static long sendPushWithPreUploadedMedia(final Context context,
                                                  final OutgoingMediaMessage message,
                                                  final Collection<PreUploadResult> preUploadResults,
                                                  final long threadId,
                                                  final SmsDatabase.InsertListener insertListener)
  {
    Log.i(TAG, "Sending media message with pre-uploads to " + message.getRecipient().getId() + ", thread: " + threadId);
    Preconditions.checkArgument(message.getAttachments().isEmpty(), "If the media is pre-uploaded, there should be no attachments on the message.");

    try {
      ThreadDatabase     threadDatabase     = DatabaseFactory.getThreadDatabase(context);
      MessageDatabase    mmsDatabase        = DatabaseFactory.getMmsDatabase(context);
      AttachmentDatabase attachmentDatabase = DatabaseFactory.getAttachmentDatabase(context);

      long allocatedThreadId;

      if (threadId == -1) {
        allocatedThreadId = threadDatabase.getOrCreateThreadIdFor(message.getRecipient(), message.getDistributionType());
      } else {
        allocatedThreadId = threadId;
      }

      Recipient recipient = message.getRecipient();
      long messageId = mmsDatabase.insertMessageOutbox(applyUniversalExpireTimerIfNecessary(context, recipient, message, allocatedThreadId),
                                                       allocatedThreadId,
                                                       false,
                                                       insertListener);

      List<AttachmentId> attachmentIds = Stream.of(preUploadResults).map(PreUploadResult::getAttachmentId).toList();
      List<String>       jobIds        = Stream.of(preUploadResults).map(PreUploadResult::getJobIds).flatMap(Stream::of).toList();

      attachmentDatabase.updateMessageId(attachmentIds, messageId);

      sendMediaMessage(context, recipient, false, messageId, jobIds);
      onMessageSent();
      threadDatabase.update(threadId, true);

      return allocatedThreadId;
    } catch (MmsException e) {
      Log.w(TAG, e);
      return threadId;
    }
  }

  public static void sendMediaBroadcast(@NonNull Context context, @NonNull List<OutgoingSecureMediaMessage> messages, @NonNull Collection<PreUploadResult> preUploadResults) {
    Log.i(TAG, "Sending media broadcast to " + Stream.of(messages).map(m -> m.getRecipient().getId()).toList());
    Preconditions.checkArgument(messages.size() > 0, "No messages!");
    Preconditions.checkArgument(Stream.of(messages).allMatch(m -> m.getAttachments().isEmpty()), "Messages can't have attachments! They should be pre-uploaded.");

    JobManager         jobManager             = ApplicationDependencies.getJobManager();
    AttachmentDatabase attachmentDatabase     = DatabaseFactory.getAttachmentDatabase(context);
    MessageDatabase    mmsDatabase            = DatabaseFactory.getMmsDatabase(context);
    ThreadDatabase     threadDatabase         = DatabaseFactory.getThreadDatabase(context);
    List<AttachmentId> preUploadAttachmentIds = Stream.of(preUploadResults).map(PreUploadResult::getAttachmentId).toList();
    List<String>       preUploadJobIds        = Stream.of(preUploadResults).map(PreUploadResult::getJobIds).flatMap(Stream::of).toList();
    List<Long>         messageIds             = new ArrayList<>(messages.size());
    List<String>       messageDependsOnIds    = new ArrayList<>(preUploadJobIds);

    mmsDatabase.beginTransaction();
    try {
      OutgoingSecureMediaMessage primaryMessage  = messages.get(0);
      long                       primaryThreadId = threadDatabase.getOrCreateThreadIdFor(primaryMessage.getRecipient(), primaryMessage.getDistributionType());
      long primaryMessageId = mmsDatabase.insertMessageOutbox(applyUniversalExpireTimerIfNecessary(context, primaryMessage.getRecipient(), primaryMessage, primaryThreadId),
                                                              primaryThreadId,
                                                              false,
                                                              null);

      attachmentDatabase.updateMessageId(preUploadAttachmentIds, primaryMessageId);
      messageIds.add(primaryMessageId);

      if (messages.size() > 0) {
        List<OutgoingSecureMediaMessage> secondaryMessages = messages.subList(1, messages.size());
        List<List<AttachmentId>>         attachmentCopies  = new ArrayList<>();
        List<DatabaseAttachment> preUploadAttachments = Stream.of(preUploadAttachmentIds)
                                                              .map(attachmentDatabase::getAttachment)
                                                              .toList();

        for (int i = 0; i < preUploadAttachmentIds.size(); i++) {
          attachmentCopies.add(new ArrayList<>(messages.size()));
        }

        for (OutgoingSecureMediaMessage secondaryMessage : secondaryMessages) {
          long allocatedThreadId = threadDatabase.getOrCreateThreadIdFor(secondaryMessage.getRecipient(), secondaryMessage.getDistributionType());
          long messageId = mmsDatabase.insertMessageOutbox(applyUniversalExpireTimerIfNecessary(context, secondaryMessage.getRecipient(), secondaryMessage, allocatedThreadId),
                                                           allocatedThreadId,
                                                           false,
                                                           null);
          List<AttachmentId> attachmentIds = new ArrayList<>(preUploadAttachmentIds.size());

          for (int i = 0; i < preUploadAttachments.size(); i++) {
            AttachmentId attachmentId = attachmentDatabase.insertAttachmentForPreUpload(preUploadAttachments.get(i)).getAttachmentId();
            attachmentCopies.get(i).add(attachmentId);
            attachmentIds.add(attachmentId);
          }

          attachmentDatabase.updateMessageId(attachmentIds, messageId);
          messageIds.add(messageId);
        }

        for (int i = 0; i < attachmentCopies.size(); i++) {
          Job copyJob = new AttachmentCopyJob(preUploadAttachmentIds.get(i), attachmentCopies.get(i));
          jobManager.add(copyJob, preUploadJobIds);
          messageDependsOnIds.add(copyJob.getId());
        }
      }

      for (int i = 0; i < messageIds.size(); i++) {
        long                       messageId = messageIds.get(i);
        OutgoingSecureMediaMessage message   = messages.get(i);
        Recipient                  recipient = message.getRecipient();

        if (isLocalSelfSend(context, recipient, false)) {
          sendLocalMediaSelf(context, messageId);
        } else if (isGroupPushSend(recipient)) {
          jobManager.add(new PushGroupSendJob(messageId, recipient.getId(), null, true), messageDependsOnIds, recipient.getId().toQueueKey());
        } else {
          jobManager.add(new PushMediaSendJob(messageId, recipient), messageDependsOnIds, recipient.getId().toQueueKey());
        }
      }

      onMessageSent();
      mmsDatabase.setTransactionSuccessful();
    } catch (MmsException e) {
      Log.w(TAG, "Failed to send messages.", e);
    } finally {
      mmsDatabase.endTransaction();
    }
  }

  /**
   * @return A result if the attachment was enqueued, or null if it failed to enqueue or shouldn't
   * be enqueued (like in the case of a local self-send).
   */
  public static @Nullable PreUploadResult preUploadPushAttachment(@NonNull Context context, @NonNull Attachment attachment, @Nullable Recipient recipient) {
    if (isLocalSelfSend(context, recipient, false)) {
      return null;
    }

    Log.i(TAG, "Pre-uploading attachment for " + (recipient != null ? recipient.getId() : "null"));

    try {
      AttachmentDatabase attachmentDatabase = DatabaseFactory.getAttachmentDatabase(context);
      DatabaseAttachment databaseAttachment = attachmentDatabase.insertAttachmentForPreUpload(attachment);

      Job compressionJob         = AttachmentCompressionJob.fromAttachment(databaseAttachment, false, -1);
      Job resumableUploadSpecJob = new ResumableUploadSpecJob();
      Job uploadJob              = new AttachmentUploadJob(databaseAttachment.getAttachmentId());

      ApplicationDependencies.getJobManager()
                             .startChain(compressionJob)
                             .then(resumableUploadSpecJob)
                             .then(uploadJob)
                             .enqueue();

      return new PreUploadResult(databaseAttachment.getAttachmentId(), Arrays.asList(compressionJob.getId(), resumableUploadSpecJob.getId(), uploadJob.getId()));
    } catch (MmsException e) {
      Log.w(TAG, "preUploadPushAttachment() - Failed to upload!", e);
      return null;
    }
  }

  public static void sendNewReaction(@NonNull Context context, long messageId, boolean isMms, @NonNull String emoji) {
    MessageDatabase db       = isMms ? DatabaseFactory.getMmsDatabase(context) : DatabaseFactory.getSmsDatabase(context);
    ReactionRecord  reaction = new ReactionRecord(emoji, Recipient.self().getId(), System.currentTimeMillis(), System.currentTimeMillis());

    db.addReaction(messageId, reaction);

    try {
      ApplicationDependencies.getJobManager().add(ReactionSendJob.create(context, messageId, isMms, reaction, false));
      onMessageSent();
    } catch (NoSuchMessageException e) {
      Log.w(TAG, "[sendNewReaction] Could not find message! Ignoring.");
    }
  }

  public static void sendReactionRemoval(@NonNull Context context, long messageId, boolean isMms, @NonNull ReactionRecord reaction) {
    MessageDatabase db = isMms ? DatabaseFactory.getMmsDatabase(context) : DatabaseFactory.getSmsDatabase(context);

    db.deleteReaction(messageId, reaction.getAuthor());

    try {
      ApplicationDependencies.getJobManager().add(ReactionSendJob.create(context, messageId, isMms, reaction, true));
      onMessageSent();
    } catch (NoSuchMessageException e) {
      Log.w(TAG, "[sendReactionRemoval] Could not find message! Ignoring.");
    }
  }

  public static void sendRemoteDelete(@NonNull Context context, long messageId, boolean isMms) {
    MessageDatabase db = isMms ? DatabaseFactory.getMmsDatabase(context) : DatabaseFactory.getSmsDatabase(context);
    db.markAsRemoteDelete(messageId);
    db.markAsSending(messageId);

    try {
      ApplicationDependencies.getJobManager().add(RemoteDeleteSendJob.create(context, messageId, isMms));
      onMessageSent();
    } catch (NoSuchMessageException e) {
      Log.w(TAG, "[sendRemoteDelete] Could not find message! Ignoring.");
    }
  }

  public static void resendGroupMessage(Context context, MessageRecord messageRecord, RecipientId filterRecipientId) {
    if (!messageRecord.isMms()) throw new AssertionError("Not Group");
    sendGroupPush(context, messageRecord.getRecipient(), messageRecord.getId(), filterRecipientId, Collections.emptyList());
    onMessageSent();
  }

  public static void resend(Context context, MessageRecord messageRecord) {
    long      messageId   = messageRecord.getId();
    boolean   forceSms    = messageRecord.isForcedSms();
    boolean   keyExchange = messageRecord.isKeyExchange();
    Recipient recipient   = messageRecord.getRecipient();

    if (messageRecord.isMms()) {
      sendMediaMessage(context, recipient, forceSms, messageId, Collections.emptyList());
    } else {
      sendTextMessage(context, recipient, forceSms, keyExchange, messageId);
    }

    onMessageSent();
  }

  public static void onMessageSent() {
    EventBus.getDefault().postSticky(MessageSentEvent.INSTANCE);
  }

  private static @NonNull OutgoingTextMessage applyUniversalExpireTimerIfNecessary(@NonNull Context context, @NonNull Recipient recipient, @NonNull OutgoingTextMessage outgoingTextMessage, long threadId) {
    if (outgoingTextMessage.getExpiresIn() == 0 && RecipientUtil.setAndSendUniversalExpireTimerIfNecessary(context, recipient, threadId)) {
      return outgoingTextMessage.withExpiry(TimeUnit.SECONDS.toMillis(SignalStore.settings().getUniversalExpireTimer()));
    }
    return outgoingTextMessage;
  }

  private static @NonNull OutgoingMediaMessage applyUniversalExpireTimerIfNecessary(@NonNull Context context, @NonNull Recipient recipient, @NonNull OutgoingMediaMessage outgoingMediaMessage, long threadId) {
    if (!outgoingMediaMessage.isExpirationUpdate() && outgoingMediaMessage.getExpiresIn() == 0 && RecipientUtil.setAndSendUniversalExpireTimerIfNecessary(context, recipient, threadId)) {
      return outgoingMediaMessage.withExpiry(TimeUnit.SECONDS.toMillis(SignalStore.settings().getUniversalExpireTimer()));
    }
    return outgoingMediaMessage;
  }

  private static void sendMediaMessage(Context context, Recipient recipient, boolean forceSms, long messageId, @NonNull Collection<String> uploadJobIds)
  {

    if (isLocalSelfSend(context, recipient, forceSms)) {
      sendLocalMediaSelf(context, messageId);
    } else if (isGroupPushSend(recipient)) {
      sendGroupPush(context, recipient, messageId, null, uploadJobIds);
    } else if (!forceSms && isPushMediaSend(context, recipient)) {
      sendMediaPush(context, recipient, messageId, uploadJobIds);
    } else {
      sendMms(context, messageId);
    }
  }

  private static void sendTextMessage(Context context, Recipient recipient,
                                      boolean forceSms, boolean keyExchange,
                                      long messageId)
  {
    if (isLocalSelfSend(context, recipient, forceSms)) {
      sendLocalTextSelf(context, messageId);
    } else if (!forceSms && isPushTextSend(context, recipient, keyExchange)) {
      sendTextPush(recipient, messageId);
    } else {
      sendSms(recipient, messageId);
    }
  }

  private static void sendTextPush(Recipient recipient, long messageId) {
    ApplicationDependencies.getJobManager().add(new PushTextSendJob(messageId, recipient));
  }

  private static void sendMediaPush(Context context, Recipient recipient, long messageId, @NonNull Collection<String> uploadJobIds) {
    JobManager jobManager = ApplicationDependencies.getJobManager();

    if (uploadJobIds.size() > 0) {
      Job mediaSend = new PushMediaSendJob(messageId, recipient);
      jobManager.add(mediaSend, uploadJobIds);
    } else {
      PushMediaSendJob.enqueue(context, jobManager, messageId, recipient);
    }
  }

  private static void sendGroupPush(Context context, Recipient recipient, long messageId, RecipientId filterRecipientId, @NonNull Collection<String> uploadJobIds) {
    JobManager jobManager = ApplicationDependencies.getJobManager();

    if (uploadJobIds.size() > 0) {
      Job groupSend = new PushGroupSendJob(messageId, recipient.getId(), filterRecipientId, !uploadJobIds.isEmpty());
      jobManager.add(groupSend, uploadJobIds, uploadJobIds.isEmpty() ? null : recipient.getId().toQueueKey());
    } else {
      PushGroupSendJob.enqueue(context, jobManager, messageId, recipient.getId(), filterRecipientId);
    }
  }

  private static void sendSms(Recipient recipient, long messageId) {
    JobManager jobManager = ApplicationDependencies.getJobManager();
    jobManager.add(new SmsSendJob(messageId, recipient));
  }

  private static void sendMms(Context context, long messageId) {
    JobManager jobManager = ApplicationDependencies.getJobManager();
    MmsSendJob.enqueue(context, jobManager, messageId);
  }

  private static boolean isPushTextSend(Context context, Recipient recipient, boolean keyExchange) {
    if (!TextSecurePreferences.isPushRegistered(context)) {
      return false;
    }

    if (keyExchange) {
      return false;
    }

    return isPushDestination(context, recipient);
  }

  private static boolean isPushMediaSend(Context context, Recipient recipient) {
    if (!TextSecurePreferences.isPushRegistered(context)) {
      return false;
    }

    if (recipient.isGroup()) {
      return false;
    }

    return isPushDestination(context, recipient);
  }

  private static boolean isGroupPushSend(Recipient recipient) {
    return recipient.isGroup() && !recipient.isMmsGroup();
  }

  private static boolean isPushDestination(Context context, Recipient destination) {
    if (destination.resolve().getRegistered() == RecipientDatabase.RegisteredState.REGISTERED) {
      return true;
    } else if (destination.resolve().getRegistered() == RecipientDatabase.RegisteredState.NOT_REGISTERED) {
      return false;
    } else {

      // we don't need this, since all recipients in the db will be registered
      //     try {
      //       RecipientDatabase.RegisteredState state = DirectoryHelper.refreshDirectoryFor(context, destination, false);
      //       return state == RecipientDatabase.RegisteredState.REGISTERED;
      //     } catch (IOException e1) {
      //       Log.w(TAG, e1);
      return false;
      //     }
    }
  }

  public static boolean isLocalSelfSend(@NonNull Context context, @Nullable Recipient recipient, boolean forceSms) {
    return recipient != null &&
           recipient.isSelf() &&
           !forceSms &&
           TextSecurePreferences.isPushRegistered(context) &&
           !TextSecurePreferences.isMultiDevice(context);
  }

  private static void sendLocalMediaSelf(Context context, long messageId) {
    try {
      ExpiringMessageManager expirationManager = ApplicationDependencies.getExpiringMessageManager();
      MessageDatabase        mmsDatabase       = DatabaseFactory.getMmsDatabase(context);
      MmsSmsDatabase         mmsSmsDatabase    = DatabaseFactory.getMmsSmsDatabase(context);
      OutgoingMediaMessage   message           = mmsDatabase.getOutgoingMessage(messageId);
      SyncMessageId          syncId            = new SyncMessageId(Recipient.self().getId(), message.getSentTimeMillis());
      List<Attachment>       attachments       = new LinkedList<>();

      attachments.addAll(message.getAttachments());

      attachments.addAll(Stream.of(message.getLinkPreviews())
                               .map(LinkPreview::getThumbnail)
                               .filter(Optional::isPresent)
                               .map(Optional::get)
                               .toList());

      attachments.addAll(Stream.of(message.getSharedContacts())
                               .map(Contact::getAvatar).withoutNulls()
                               .map(Contact.Avatar::getAttachment).withoutNulls()
                               .toList());

      List<AttachmentCompressionJob> compressionJobs = Stream.of(attachments)
                                                             .map(a -> AttachmentCompressionJob.fromAttachment((DatabaseAttachment) a, false, -1))
                                                             .toList();

      List<AttachmentMarkUploadedJob> fakeUploadJobs = Stream.of(attachments)
                                                             .map(a -> new AttachmentMarkUploadedJob(messageId, ((DatabaseAttachment) a).getAttachmentId()))
                                                             .toList();

      ApplicationDependencies.getJobManager().startChain(compressionJobs)
                             .then(fakeUploadJobs)
                             .enqueue();

      mmsDatabase.markAsSent(messageId, true);
      mmsDatabase.markUnidentified(messageId, true);

      mmsSmsDatabase.incrementDeliveryReceiptCount(syncId, System.currentTimeMillis());
      mmsSmsDatabase.incrementReadReceiptCount(syncId, System.currentTimeMillis());
      mmsSmsDatabase.incrementViewedReceiptCount(syncId, System.currentTimeMillis());

      if (message.getExpiresIn() > 0 && !message.isExpirationUpdate()) {
        mmsDatabase.markExpireStarted(messageId);
        expirationManager.scheduleDeletion(messageId, true, message.getExpiresIn());
      }
    } catch (NoSuchMessageException | MmsException e) {
      Log.w(TAG, "Failed to update self-sent message.", e);
    }
  }

  private static void sendLocalTextSelf(Context context, long messageId) {
    try {
      ExpiringMessageManager expirationManager = ApplicationDependencies.getExpiringMessageManager();
      MessageDatabase        smsDatabase       = DatabaseFactory.getSmsDatabase(context);
      MmsSmsDatabase         mmsSmsDatabase    = DatabaseFactory.getMmsSmsDatabase(context);
      SmsMessageRecord       message           = smsDatabase.getSmsMessage(messageId);
      SyncMessageId          syncId            = new SyncMessageId(Recipient.self().getId(), message.getDateSent());

      smsDatabase.markAsSent(messageId, true);
      smsDatabase.markUnidentified(messageId, true);

      mmsSmsDatabase.incrementDeliveryReceiptCount(syncId, System.currentTimeMillis());
      mmsSmsDatabase.incrementReadReceiptCount(syncId, System.currentTimeMillis());

      if (message.getExpiresIn() > 0) {
        smsDatabase.markExpireStarted(messageId);
        expirationManager.scheduleDeletion(message.getId(), message.isMms(), message.getExpiresIn());
      }
    } catch (NoSuchMessageException e) {
      Log.w(TAG, "Failed to update self-sent message.", e);
    }
  }

  public static class PreUploadResult implements Parcelable {
    private final AttachmentId       attachmentId;
    private final Collection<String> jobIds;

    PreUploadResult(@NonNull AttachmentId attachmentId, @NonNull Collection<String> jobIds) {
      this.attachmentId = attachmentId;
      this.jobIds       = jobIds;
    }

    private PreUploadResult(Parcel in) {
      this.attachmentId = in.readParcelable(AttachmentId.class.getClassLoader());
      this.jobIds       = ParcelUtil.readStringCollection(in);
    }

    public @NonNull AttachmentId getAttachmentId() {
      return attachmentId;
    }

    public @NonNull Collection<String> getJobIds() {
      return jobIds;
    }

    public static final Creator<PreUploadResult> CREATOR = new Creator<PreUploadResult>() {
      @Override
      public PreUploadResult createFromParcel(Parcel in) {
        return new PreUploadResult(in);
      }

      @Override
      public PreUploadResult[] newArray(int size) {
        return new PreUploadResult[size];
      }
    };

    @Override
    public int describeContents() {
      return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
      dest.writeParcelable(attachmentId, flags);
      ParcelUtil.writeStringCollection(dest, jobIds);
    }
  }

  public enum MessageSentEvent {
    INSTANCE
  }
}