package su.sres.securesms.messages;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;
import com.mobilecoin.lib.exceptions.SerializationException;

import su.sres.core.util.logging.Log;

import org.signal.ringrtc.CallId;
import org.signal.zkgroup.profiles.ProfileKey;

import su.sres.securesms.attachments.Attachment;
import su.sres.securesms.attachments.DatabaseAttachment;
import su.sres.securesms.attachments.PointerAttachment;
import su.sres.securesms.attachments.TombstoneAttachment;
import su.sres.securesms.attachments.UriAttachment;
import su.sres.securesms.components.emoji.EmojiUtil;
import su.sres.securesms.contactshare.Contact;
import su.sres.securesms.contactshare.ContactModelMapper;
import su.sres.securesms.crypto.ProfileKeyUtil;
import su.sres.securesms.crypto.SecurityEvent;
import su.sres.securesms.crypto.SessionUtil;
import su.sres.securesms.database.AttachmentDatabase;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.GroupDatabase;
import su.sres.securesms.database.GroupDatabase.GroupRecord;
import su.sres.securesms.database.GroupReceiptDatabase;
import su.sres.securesms.database.GroupReceiptDatabase.GroupReceiptInfo;
import su.sres.securesms.database.MessageDatabase;
import su.sres.securesms.database.MessageDatabase.InsertResult;
import su.sres.securesms.database.MessageDatabase.SyncMessageId;
import su.sres.securesms.database.MmsSmsDatabase;
import su.sres.securesms.database.PaymentDatabase;
import su.sres.securesms.database.PaymentMetaDataUtil;
import su.sres.securesms.database.RecipientDatabase;
import su.sres.securesms.database.StickerDatabase;
import su.sres.securesms.database.ThreadDatabase;
import su.sres.securesms.database.model.Mention;
import su.sres.securesms.database.model.MessageId;
import su.sres.securesms.database.model.MessageLogEntry;
import su.sres.securesms.database.model.MessageRecord;
import su.sres.securesms.database.model.MmsMessageRecord;
import su.sres.securesms.database.model.PendingRetryReceiptModel;
import su.sres.securesms.database.model.ReactionRecord;
import su.sres.securesms.database.model.StickerRecord;
import su.sres.securesms.database.model.ThreadRecord;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.groups.BadGroupIdException;
import su.sres.securesms.groups.GroupChangeBusyException;
import su.sres.securesms.groups.GroupId;
import su.sres.securesms.groups.GroupManager;
import su.sres.securesms.groups.GroupNotAMemberException;
import su.sres.securesms.groups.GroupV1MessageProcessor;
import su.sres.securesms.groups.GroupsV1MigrationUtil;
import su.sres.securesms.jobmanager.JobManager;
import su.sres.securesms.jobs.AttachmentDownloadJob;
import su.sres.securesms.jobs.AutomaticSessionResetJob;
import su.sres.securesms.jobs.GroupCallPeekJob;
import su.sres.securesms.jobs.GroupV2UpdateSelfProfileKeyJob;
import su.sres.securesms.jobs.MultiDeviceBlockedUpdateJob;
import su.sres.securesms.jobs.MultiDeviceConfigurationUpdateJob;
import su.sres.securesms.jobs.MultiDeviceContactUpdateJob;
import su.sres.securesms.jobs.MultiDeviceGroupUpdateJob;
import su.sres.securesms.jobs.MultiDeviceKeysUpdateJob;
import su.sres.securesms.jobs.MultiDeviceStickerPackSyncJob;
import su.sres.securesms.jobs.NullMessageSendJob;
import su.sres.securesms.jobs.PaymentLedgerUpdateJob;
import su.sres.securesms.jobs.PaymentTransactionCheckJob;
import su.sres.securesms.jobs.ProfileKeySendJob;
import su.sres.securesms.jobs.PushProcessMessageJob;
import su.sres.securesms.jobs.RefreshAttributesJob;
import su.sres.securesms.jobs.RefreshOwnProfileJob;
import su.sres.securesms.jobs.RequestGroupInfoJob;
import su.sres.securesms.jobs.ResendMessageJob;
import su.sres.securesms.jobs.RetrieveProfileJob;
import su.sres.securesms.jobs.SendDeliveryReceiptJob;
import su.sres.securesms.jobs.SenderKeyDistributionSendJob;
import su.sres.securesms.jobs.StickerPackDownloadJob;
import su.sres.securesms.jobs.TrimThreadJob;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.securesms.linkpreview.LinkPreview;
import su.sres.securesms.linkpreview.LinkPreviewUtil;
import su.sres.securesms.mms.IncomingMediaMessage;
import su.sres.securesms.mms.MmsException;
import su.sres.securesms.mms.OutgoingExpirationUpdateMessage;
import su.sres.securesms.mms.OutgoingMediaMessage;
import su.sres.securesms.mms.OutgoingSecureMediaMessage;
import su.sres.securesms.mms.QuoteModel;
import su.sres.securesms.mms.SlideDeck;
import su.sres.securesms.mms.StickerSlide;
import su.sres.securesms.notifications.MarkReadReceiver;
import su.sres.securesms.notifications.MessageNotifier;
import su.sres.securesms.payments.MobileCoinPublicAddress;
import su.sres.securesms.ratelimit.RateLimitUtil;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.recipients.RecipientUtil;
import su.sres.securesms.ringrtc.RemotePeer;
import su.sres.securesms.service.webrtc.WebRtcData;
import su.sres.securesms.sms.IncomingEncryptedMessage;
import su.sres.securesms.sms.IncomingEndSessionMessage;
import su.sres.securesms.sms.IncomingTextMessage;
import su.sres.securesms.sms.OutgoingEncryptedMessage;
import su.sres.securesms.sms.OutgoingEndSessionMessage;
import su.sres.securesms.sms.OutgoingTextMessage;
import su.sres.securesms.stickers.StickerLocator;
import su.sres.securesms.util.FeatureFlags;
import su.sres.securesms.util.GroupUtil;
import su.sres.securesms.util.Hex;
import su.sres.securesms.util.IdentityUtil;
import su.sres.securesms.util.MediaUtil;
import su.sres.securesms.util.RemoteDeleteUtil;
import su.sres.securesms.util.TextSecurePreferences;
import su.sres.securesms.util.Util;

import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.protocol.DecryptionErrorMessage;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;

import su.sres.signalservice.api.messages.SignalServiceAttachment;
import su.sres.signalservice.api.messages.SignalServiceContent;
import su.sres.signalservice.api.messages.SignalServiceDataMessage;
import su.sres.signalservice.api.messages.SignalServiceGroup;
import su.sres.signalservice.api.messages.SignalServiceGroupContext;
import su.sres.signalservice.api.messages.SignalServiceGroupV2;
import su.sres.signalservice.api.messages.SignalServiceReceiptMessage;
import su.sres.signalservice.api.messages.SignalServiceTypingMessage;
import su.sres.signalservice.api.messages.calls.AnswerMessage;
import su.sres.signalservice.api.messages.calls.BusyMessage;
import su.sres.signalservice.api.messages.calls.HangupMessage;
import su.sres.signalservice.api.messages.calls.IceUpdateMessage;
import su.sres.signalservice.api.messages.calls.OfferMessage;
import su.sres.signalservice.api.messages.calls.OpaqueMessage;
import su.sres.signalservice.api.messages.calls.SignalServiceCallMessage;
import su.sres.signalservice.api.messages.multidevice.BlockedListMessage;
import su.sres.signalservice.api.messages.multidevice.ConfigurationMessage;
import su.sres.signalservice.api.messages.multidevice.MessageRequestResponseMessage;
import su.sres.signalservice.api.messages.multidevice.OutgoingPaymentMessage;
import su.sres.signalservice.api.messages.multidevice.ReadMessage;
import su.sres.signalservice.api.messages.multidevice.RequestMessage;
import su.sres.signalservice.api.messages.multidevice.SentTranscriptMessage;
import su.sres.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import su.sres.signalservice.api.messages.multidevice.StickerPackOperationMessage;
import su.sres.signalservice.api.messages.multidevice.VerifiedMessage;
import su.sres.signalservice.api.messages.multidevice.ViewOnceOpenMessage;
import su.sres.signalservice.api.messages.multidevice.ViewedMessage;
import su.sres.signalservice.api.messages.shared.SharedContact;
import su.sres.signalservice.api.payments.Money;
import su.sres.signalservice.api.push.DistributionId;
import su.sres.signalservice.api.push.SignalServiceAddress;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Takes data about a decrypted message, transforms it into user-presentable data, and writes that
 * data to our data stores.
 */
public final class MessageContentProcessor {

  private static final String TAG = Log.tag(MessageContentProcessor.class);

  private final Context context;

  public MessageContentProcessor(@NonNull Context context) {
    this.context = context;
  }

  /**
   * Given the details about a message decryption, this will insert the proper message content into
   * the database.
   * <p>
   * This is super-stateful, and it's recommended that this be run in a transaction so that no
   * intermediate results are persisted to the database if the app were to crash.
   */
  public void process(MessageState messageState, @Nullable SignalServiceContent content, @Nullable ExceptionMetadata exceptionMetadata, long timestamp, long smsMessageId)
      throws IOException, GroupChangeBusyException
  {
    Optional<Long> optionalSmsMessageId = smsMessageId > 0 ? Optional.of(smsMessageId) : Optional.absent();

    if (messageState == MessageState.DECRYPTED_OK) {

      if (content != null) {
        Recipient senderRecipient = Recipient.externalHighTrustPush(context, content.getSender());

        handleMessage(content, timestamp, senderRecipient, optionalSmsMessageId);

        Optional<List<SignalServiceContent>> earlyContent = ApplicationDependencies.getEarlyMessageCache()
                                                                                   .retrieve(senderRecipient.getId(), content.getTimestamp());
        if (earlyContent.isPresent()) {
          log(String.valueOf(content.getTimestamp()), "Found " + earlyContent.get().size() + " dependent item(s) that were retrieved earlier. Processing.");

          for (SignalServiceContent earlyItem : earlyContent.get()) {
            handleMessage(earlyItem, timestamp, senderRecipient, Optional.absent());
          }
        }
      } else {
        warn("null", "Null content. Ignoring message.");
      }
    } else if (exceptionMetadata != null) {
      handleExceptionMessage(messageState, exceptionMetadata, timestamp, optionalSmsMessageId);
    } else if (messageState == MessageState.NOOP) {
      Log.d(TAG, "Nothing to do: " + messageState.name());
    } else {
      warn("Bad state! messageState: " + messageState);
    }
  }

  private void handleMessage(@NonNull SignalServiceContent content, long timestamp, @NonNull Recipient senderRecipient, @NonNull Optional<Long> smsMessageId)
      throws IOException, GroupChangeBusyException
  {
    try {
      Recipient threadRecipient = getMessageDestination(content);

      if (shouldIgnore(content, senderRecipient, threadRecipient)) {
        log(content.getTimestamp(), "Ignoring message.");
        return;
      }

      PendingRetryReceiptModel pending      = ApplicationDependencies.getPendingRetryReceiptCache().get(senderRecipient.getId(), content.getTimestamp());
      long                     receivedTime = handlePendingRetry(pending, content, threadRecipient);

      log(String.valueOf(content.getTimestamp()), "Beginning message processing. Sender: " + formatSender(senderRecipient, content));

      if (content.getDataMessage().isPresent()) {
        GroupDatabase            groupDatabase = DatabaseFactory.getGroupDatabase(context);
        SignalServiceDataMessage message       = content.getDataMessage().get();
        boolean isMediaMessage = message.getAttachments().isPresent() || message.getQuote().isPresent() || message.getSharedContacts().isPresent() || message.getPreviews().isPresent() || message.getSticker()
                                                                                                                                                                                                  .isPresent() || message
                                     .getMentions().isPresent();
        Optional<GroupId> groupId      = GroupUtil.idFromGroupContext(message.getGroupContext());
        boolean           isGv2Message = groupId.isPresent() && groupId.get().isV2();

        if (isGv2Message) {
          if (handleGv2PreProcessing(groupId.orNull().requireV2(), content, content.getDataMessage().get().getGroupContext().get().getGroupV2().get(), senderRecipient)) {
            return;
          }
        }

        MessageId messageId = null;

        if (isInvalidMessage(message)) handleInvalidMessage(content.getSender(), content.getSenderDevice(), groupId, content.getTimestamp(), smsMessageId);
        else if (message.isEndSession()) messageId = handleEndSessionMessage(content, smsMessageId, senderRecipient);
        else if (message.isGroupV1Update()) handleGroupV1Message(content, message, smsMessageId, groupId.get().requireV1(), senderRecipient, threadRecipient, receivedTime);
        else if (message.isExpirationUpdate()) messageId = handleExpirationUpdate(content, message, smsMessageId, groupId, senderRecipient, threadRecipient, receivedTime);
        else if (message.getReaction().isPresent()) messageId = handleReaction(content, message, senderRecipient);
        else if (message.getRemoteDelete().isPresent()) messageId = handleRemoteDelete(content, message, senderRecipient);
        else if (message.getPayment().isPresent()) handlePayment(content, message, senderRecipient);
        else if (isMediaMessage) messageId = handleMediaMessage(content, message, smsMessageId, senderRecipient, threadRecipient, receivedTime);
        else if (message.getBody().isPresent()) messageId = handleTextMessage(content, message, smsMessageId, groupId, senderRecipient, threadRecipient, receivedTime);
        else if (message.getGroupCallUpdate().isPresent()) handleGroupCallUpdateMessage(content, message, groupId, senderRecipient);

        if (groupId.isPresent() && groupDatabase.isUnknownGroup(groupId.get())) {
          handleUnknownGroupMessage(content, message.getGroupContext().get(), senderRecipient);
        }

        if (message.getProfileKey().isPresent()) {
          handleProfileKey(content, message.getProfileKey().get(), senderRecipient);
        }

        if (content.isNeedsReceipt() && messageId != null) {
          handleNeedsDeliveryReceipt(content, message, messageId);
        } else if (!content.isNeedsReceipt()) {
          if (RecipientUtil.shouldHaveProfileKey(context, threadRecipient)) {
            Log.w(TAG, "Received an unsealed sender message from " + senderRecipient.getId() + ", but they should already have our profile key. Correcting.");

            if (groupId.isPresent() && groupId.get().isV2()) {
              Log.i(TAG, "Message was to a GV2 group. Ensuring our group profile keys are up to date.");
              ApplicationDependencies.getJobManager().startChain(new RefreshAttributesJob(false))
                                     .then(GroupV2UpdateSelfProfileKeyJob.withQueueLimits(groupId.get().requireV2()))
                                     .enqueue();
            } else if (!threadRecipient.isGroup()) {
              Log.i(TAG, "Message was to a 1:1. Ensuring this user has our profile key.");
              ApplicationDependencies.getJobManager().startChain(new RefreshAttributesJob(false))
                                     .then(ProfileKeySendJob.create(context, DatabaseFactory.getThreadDatabase(context).getOrCreateThreadIdFor(threadRecipient), true))
                                     .enqueue();
            }
          }
        }
      } else if (content.getSyncMessage().isPresent()) {
        TextSecurePreferences.setMultiDevice(context, true);

        SignalServiceSyncMessage syncMessage = content.getSyncMessage().get();

        if (syncMessage.getSent().isPresent()) handleSynchronizeSentMessage(content, syncMessage.getSent().get(), senderRecipient);
        else if (syncMessage.getRequest().isPresent()) handleSynchronizeRequestMessage(syncMessage.getRequest().get());
        else if (syncMessage.getRead().isPresent()) handleSynchronizeReadMessage(syncMessage.getRead().get(), content.getTimestamp(), senderRecipient);
        else if (syncMessage.getViewed().isPresent()) handleSynchronizeViewedMessage(syncMessage.getViewed().get(), content.getTimestamp());
        else if (syncMessage.getViewOnceOpen().isPresent()) handleSynchronizeViewOnceOpenMessage(syncMessage.getViewOnceOpen().get(), content.getTimestamp());
        else if (syncMessage.getVerified().isPresent()) handleSynchronizeVerifiedMessage(syncMessage.getVerified().get());
        else if (syncMessage.getStickerPackOperations().isPresent()) handleSynchronizeStickerPackOperation(syncMessage.getStickerPackOperations().get());
        else if (syncMessage.getConfiguration().isPresent()) handleSynchronizeConfigurationMessage(syncMessage.getConfiguration().get());
        else if (syncMessage.getBlockedList().isPresent()) handleSynchronizeBlockedListMessage(syncMessage.getBlockedList().get());
        else if (syncMessage.getFetchType().isPresent()) handleSynchronizeFetchMessage(syncMessage.getFetchType().get());
        else if (syncMessage.getMessageRequestResponse().isPresent()) handleSynchronizeMessageRequestResponse(syncMessage.getMessageRequestResponse().get());
        else if (syncMessage.getOutgoingPaymentMessage().isPresent()) handleSynchronizeOutgoingPayment(content, syncMessage.getOutgoingPaymentMessage().get());
        else warn(String.valueOf(content.getTimestamp()), "Contains no known sync types...");
      } else if (content.getCallMessage().isPresent()) {
        log(String.valueOf(content.getTimestamp()), "Got call message...");

        SignalServiceCallMessage message             = content.getCallMessage().get();
        Optional<Integer>        destinationDeviceId = message.getDestinationDeviceId();

        if (destinationDeviceId.isPresent() && destinationDeviceId.get() != 1) {
          log(String.valueOf(content.getTimestamp()), String.format(Locale.US, "Ignoring call message that is not for this device! intended: %d, this: %d", destinationDeviceId.get(), 1));
          return;
        }

        if (message.getOfferMessage().isPresent()) handleCallOfferMessage(content, message.getOfferMessage().get(), smsMessageId, senderRecipient);
        else if (message.getAnswerMessage().isPresent()) handleCallAnswerMessage(content, message.getAnswerMessage().get(), senderRecipient);
        else if (message.getIceUpdateMessages().isPresent()) handleCallIceUpdateMessage(content, message.getIceUpdateMessages().get(), senderRecipient);
        else if (message.getHangupMessage().isPresent()) handleCallHangupMessage(content, message.getHangupMessage().get(), smsMessageId, senderRecipient);
        else if (message.getBusyMessage().isPresent()) handleCallBusyMessage(content, message.getBusyMessage().get(), senderRecipient);
        else if (message.getOpaqueMessage().isPresent()) handleCallOpaqueMessage(content, message.getOpaqueMessage().get(), senderRecipient);
      } else if (content.getReceiptMessage().isPresent()) {
        SignalServiceReceiptMessage message = content.getReceiptMessage().get();

        if (message.isReadReceipt()) handleReadReceipt(content, message, senderRecipient);
        else if (message.isDeliveryReceipt()) handleDeliveryReceipt(content, message, senderRecipient);
        else if (message.isViewedReceipt()) handleViewedReceipt(content, message, senderRecipient);
      } else if (content.getTypingMessage().isPresent()) {
        handleTypingMessage(content, content.getTypingMessage().get(), senderRecipient);
      } else if (content.getDecryptionErrorMessage().isPresent()) {
        handleRetryReceipt(content, content.getDecryptionErrorMessage().get(), senderRecipient);
      } else if (content.getSenderKeyDistributionMessage().isPresent()) {
        // Already handled, here in order to prevent unrecognized message log
      } else {
        warn(String.valueOf(content.getTimestamp()), "Got unrecognized message!");
      }

      resetRecipientToPush(senderRecipient);

      if (pending != null) {
        warn(content.getTimestamp(), "Pending retry was processed. Deleting.");
        ApplicationDependencies.getPendingRetryReceiptCache().delete(pending);
      }
    } catch (StorageFailedException e) {
      warn(String.valueOf(content.getTimestamp()), e);
      handleCorruptMessage(e.getSender(), e.getSenderDevice(), timestamp, smsMessageId);
    } catch (BadGroupIdException e) {
      warn(String.valueOf(content.getTimestamp()), "Ignoring message with bad group id", e);
    }
  }

  private long handlePendingRetry(@Nullable PendingRetryReceiptModel pending, @NonNull SignalServiceContent content, @NonNull Recipient destination) throws BadGroupIdException {
    long receivedTime = System.currentTimeMillis();

    if (pending != null) {
      warn(content.getTimestamp(), "Incoming message matches a pending retry we were expecting.");

      Long threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(destination.getId());

      if (threadId != null) {
        ThreadDatabase.ConversationMetadata metadata      = DatabaseFactory.getThreadDatabase(context).getConversationMetadata(threadId);
        long                                visibleThread = ApplicationDependencies.getMessageNotifier().getVisibleThread();

        if (threadId != visibleThread && metadata.getLastSeen() > 0 && metadata.getLastSeen() < pending.getReceivedTimestamp()) {
          receivedTime = pending.getReceivedTimestamp();
          warn(content.getTimestamp(), "Thread has not been opened yet. Using received timestamp of " + receivedTime);
        } else {
          warn(content.getTimestamp(), "Thread was opened after receiving the original message. Using the current time for received time. (Last seen: " + metadata.getLastSeen() + ", ThreadVisible: " + (threadId == visibleThread) + ")");
        }
      } else {
        warn(content.getTimestamp(), "Could not find a thread for the pending message. Using current time for received time.");
      }
    }

    return receivedTime;
  }

  private void handlePayment(@NonNull SignalServiceContent content, @NonNull SignalServiceDataMessage message, @NonNull Recipient senderRecipient) {
    if (!message.getPayment().isPresent()) {
      throw new AssertionError();
    }

    if (!message.getPayment().get().getPaymentNotification().isPresent()) {
      warn(content.getTimestamp(), "Ignoring payment message without notification");
      return;
    }

    SignalServiceDataMessage.PaymentNotification paymentNotification = message.getPayment().get().getPaymentNotification().get();
    PaymentDatabase                              paymentDatabase     = DatabaseFactory.getPaymentDatabase(context);
    UUID                                         uuid                = UUID.randomUUID();
    String                                       queue               = "Payment_" + PushProcessMessageJob.getQueueName(senderRecipient.getId());

    try {
      paymentDatabase.createIncomingPayment(uuid,
                                            senderRecipient.getId(),
                                            message.getTimestamp(),
                                            paymentNotification.getNote(),
                                            Money.MobileCoin.ZERO,
                                            Money.MobileCoin.ZERO,
                                            paymentNotification.getReceipt());
    } catch (PaymentDatabase.PublicKeyConflictException e) {
      warn(content.getTimestamp(), "Ignoring payment with public key already in database");
      return;
    } catch (SerializationException e) {
      warn(content.getTimestamp(), "Ignoring payment with bad data.", e);
    }

    ApplicationDependencies.getJobManager()
                           .startChain(new PaymentTransactionCheckJob(uuid, queue))
                           .then(PaymentLedgerUpdateJob.updateLedger())
                           .enqueue();
  }

  /**
   * @return True if the content should be ignored, otherwise false.
   */
  private boolean handleGv2PreProcessing(@NonNull GroupId.V2 groupId, @NonNull SignalServiceContent content, @NonNull SignalServiceGroupV2 groupV2, @NonNull Recipient senderRecipient)
      throws IOException, GroupChangeBusyException
  {
    GroupDatabase         groupDatabase = DatabaseFactory.getGroupDatabase(context);
    Optional<GroupRecord> possibleGv1   = groupDatabase.getGroupV1ByExpectedV2(groupId);

    if (possibleGv1.isPresent()) {
      GroupsV1MigrationUtil.performLocalMigration(context, possibleGv1.get().getId().requireV1());
    }

    if (!updateGv2GroupFromServerOrP2PChange(content, groupV2)) {
      log(String.valueOf(content.getTimestamp()), "Ignoring GV2 message from member not in group " + groupId + ". Sender: " + senderRecipient.getId() + " | " + senderRecipient.requireServiceId());
      return true;
    }

    Optional<GroupRecord> groupRecord = groupDatabase.getGroup(groupId);

    if (groupRecord.isPresent() && !groupRecord.get().getMembers().contains(senderRecipient.getId())) {
      log(String.valueOf(content.getTimestamp()), "Ignoring GV2 message from member not in group " + groupId);
      return true;
    }

    if (groupRecord.isPresent() && groupRecord.get().isAnnouncementGroup() && !groupRecord.get().getAdmins().contains(senderRecipient)) {
      if (content.getDataMessage().isPresent()) {
        SignalServiceDataMessage data = content.getDataMessage().get();
        if (data.getBody().isPresent() ||
            data.getAttachments().isPresent() ||
            data.getQuote().isPresent() ||
            data.getPreviews().isPresent() ||
            data.getMentions().isPresent() ||
            data.getSticker().isPresent())
        {
          Log.w(TAG, "Ignoring message from " + senderRecipient.getId() + " because it has disallowed content, and they're not an admin in an announcement-only group.");
          return true;
        }
      } else if (content.getTypingMessage().isPresent()) {
        Log.w(TAG, "Ignoring typing indicator from " + senderRecipient.getId() + " because they're not an admin in an announcement-only group.");
        return true;
      }
    }

    return false;
  }


  private static @Nullable SignalServiceGroupContext getGroupContextIfPresent(@NonNull SignalServiceContent content) {
    if (content.getDataMessage().isPresent() && content.getDataMessage().get().getGroupContext().isPresent()) {
      return content.getDataMessage().get().getGroupContext().get();
    } else if (content.getSyncMessage().isPresent() &&
               content.getSyncMessage().get().getSent().isPresent() &&
               content.getSyncMessage().get().getSent().get().getMessage().getGroupContext().isPresent())
    {
      return content.getSyncMessage().get().getSent().get().getMessage().getGroupContext().get();
    } else {
      return null;
    }
  }

  /**
   * Attempts to update the group to the revision mentioned in the message.
   * If the local version is at least the revision in the message it will not query the server.
   * If the message includes a signed change proto that is sufficient (i.e. local revision is only
   * 1 revision behind), it will also not query the server in this case.
   *
   * @return false iff needed to query the server and was not able to because self is not a current
   * member of the group.
   */
  private boolean updateGv2GroupFromServerOrP2PChange(@NonNull SignalServiceContent content,
                                                      @NonNull SignalServiceGroupV2 groupV2)
      throws IOException, GroupChangeBusyException
  {
    try {
      GroupManager.updateGroupFromServer(context, groupV2.getMasterKey(), groupV2.getRevision(), content.getTimestamp(), groupV2.getSignedGroupChange());
      return true;
    } catch (GroupNotAMemberException e) {
      warn(String.valueOf(content.getTimestamp()), "Ignoring message for a group we're not in");
      return false;
    }
  }

  private void handleExceptionMessage(@NonNull MessageState messageState, @NonNull ExceptionMetadata e, long timestamp, @NonNull Optional<Long> smsMessageId) {
    Recipient sender = Recipient.external(context, e.sender);

    if (sender.isBlocked()) {
      warn("Ignoring exception content from blocked sender, message state:" + messageState);
      return;
    }

    switch (messageState) {
      case INVALID_VERSION:
        warn(String.valueOf(timestamp), "Handling invalid version.");
        handleInvalidVersionMessage(e.sender, e.senderDevice, timestamp, smsMessageId);
        break;

      case LEGACY_MESSAGE:
        warn(String.valueOf(timestamp), "Handling legacy message.");
        handleLegacyMessage(e.sender, e.senderDevice, timestamp, smsMessageId);
        break;

      case DUPLICATE_MESSAGE:
        warn(String.valueOf(timestamp), "Duplicate message. Dropping.");
        break;

      case UNSUPPORTED_DATA_MESSAGE:
        warn(String.valueOf(timestamp), "Handling unsupported data message.");
        handleUnsupportedDataMessage(e.sender, e.senderDevice, Optional.fromNullable(e.groupId), timestamp, smsMessageId);
        break;

      case CORRUPT_MESSAGE:
      case NO_SESSION:
        warn(String.valueOf(timestamp), "Discovered old enqueued bad encrypted message. Scheduling reset.");
        ApplicationDependencies.getJobManager().add(new AutomaticSessionResetJob(sender.getId(), e.senderDevice, timestamp));
        break;

      default:
        throw new AssertionError("Not handled " + messageState + ". (" + timestamp + ")");
    }
  }

  private void handleCallOfferMessage(@NonNull SignalServiceContent content,
                                      @NonNull OfferMessage message,
                                      @NonNull Optional<Long> smsMessageId,
                                      @NonNull Recipient senderRecipient)
  {
    log(String.valueOf(content.getTimestamp()), "handleCallOfferMessage...");

    if (smsMessageId.isPresent()) {
      MessageDatabase database = DatabaseFactory.getSmsDatabase(context);
      database.markAsMissedCall(smsMessageId.get(), message.getType() == OfferMessage.Type.VIDEO_CALL);
    } else {
      RemotePeer remotePeer        = new RemotePeer(senderRecipient.getId());
      byte[]     remoteIdentityKey = ApplicationDependencies.getIdentityStore().getIdentityRecord(senderRecipient.getId()).transform(record -> record.getIdentityKey().serialize()).orNull();

      ApplicationDependencies.getSignalCallManager()
                             .receivedOffer(new WebRtcData.CallMetadata(remotePeer, new CallId(message.getId()), content.getSenderDevice()),
                                            new WebRtcData.OfferMetadata(message.getOpaque(), message.getSdp(), message.getType()),
                                            new WebRtcData.ReceivedOfferMetadata(remoteIdentityKey,
                                                                                 content.getServerReceivedTimestamp(),
                                                                                 content.getServerDeliveredTimestamp(),
                                                                                 content.getCallMessage().get().isMultiRing()));
    }
  }

  private void handleCallAnswerMessage(@NonNull SignalServiceContent content,
                                       @NonNull AnswerMessage message,
                                       @NonNull Recipient senderRecipient)
  {
    log(String.valueOf(content), "handleCallAnswerMessage...");
    RemotePeer remotePeer        = new RemotePeer(senderRecipient.getId());
    byte[]     remoteIdentityKey = ApplicationDependencies.getIdentityStore().getIdentityRecord(senderRecipient.getId()).transform(record -> record.getIdentityKey().serialize()).orNull();

    ApplicationDependencies.getSignalCallManager()
                           .receivedAnswer(new WebRtcData.CallMetadata(remotePeer, new CallId(message.getId()), content.getSenderDevice()),
                                           new WebRtcData.AnswerMetadata(message.getOpaque(), message.getSdp()),
                                           new WebRtcData.ReceivedAnswerMetadata(remoteIdentityKey, content.getCallMessage().get().isMultiRing()));
  }

  private void handleCallIceUpdateMessage(@NonNull SignalServiceContent content,
                                          @NonNull List<IceUpdateMessage> messages,
                                          @NonNull Recipient senderRecipient)
  {
    log(String.valueOf(content), "handleCallIceUpdateMessage... " + messages.size());

    List<byte[]> iceCandidates = new ArrayList<>(messages.size());
    long         callId        = -1;
    for (IceUpdateMessage iceMessage : messages) {
      iceCandidates.add(iceMessage.getOpaque());
      callId = iceMessage.getId();
    }

    RemotePeer remotePeer = new RemotePeer(senderRecipient.getId());

    ApplicationDependencies.getSignalCallManager()
                           .receivedIceCandidates(new WebRtcData.CallMetadata(remotePeer, new CallId(callId), content.getSenderDevice()),
                                                  iceCandidates);
  }

  private void handleCallHangupMessage(@NonNull SignalServiceContent content,
                                       @NonNull HangupMessage message,
                                       @NonNull Optional<Long> smsMessageId,
                                       @NonNull Recipient senderRecipient)
  {
    log(String.valueOf(content), "handleCallHangupMessage");
    if (smsMessageId.isPresent()) {
      DatabaseFactory.getSmsDatabase(context).markAsMissedCall(smsMessageId.get(), false);
    } else {
      RemotePeer remotePeer = new RemotePeer(senderRecipient.getId());

      ApplicationDependencies.getSignalCallManager()
                             .receivedCallHangup(new WebRtcData.CallMetadata(remotePeer, new CallId(message.getId()), content.getSenderDevice()),
                                                 new WebRtcData.HangupMetadata(message.getType(), message.isLegacy(), message.getDeviceId()));
    }
  }

  private void handleCallBusyMessage(@NonNull SignalServiceContent content,
                                     @NonNull BusyMessage message,
                                     @NonNull Recipient senderRecipient)
  {
    log(String.valueOf(content.getTimestamp()), "handleCallBusyMessage");

    RemotePeer remotePeer = new RemotePeer(senderRecipient.getId());

    ApplicationDependencies.getSignalCallManager()
                           .receivedCallBusy(new WebRtcData.CallMetadata(remotePeer, new CallId(message.getId()), content.getSenderDevice()));
  }

  private void handleCallOpaqueMessage(@NonNull SignalServiceContent content,
                                       @NonNull OpaqueMessage message,
                                       @NonNull Recipient senderRecipient)
  {
    log(String.valueOf(content.getTimestamp()), "handleCallOpaqueMessage");

    long messageAgeSeconds = 0;
    if (content.getServerReceivedTimestamp() > 0 && content.getServerDeliveredTimestamp() >= content.getServerReceivedTimestamp()) {
      messageAgeSeconds = (content.getServerDeliveredTimestamp() - content.getServerReceivedTimestamp()) / 1000;
    }

    ApplicationDependencies.getSignalCallManager()
                           .receivedOpaqueMessage(new WebRtcData.OpaqueMessageMetadata(senderRecipient.requireAci().uuid(),
                                                                                       message.getOpaque(),
                                                                                       content.getSenderDevice(),
                                                                                       messageAgeSeconds));
  }

  private void handleGroupCallUpdateMessage(@NonNull SignalServiceContent content,
                                            @NonNull SignalServiceDataMessage message,
                                            @NonNull Optional<GroupId> groupId,
                                            @NonNull Recipient senderRecipient)
  {
    if (!groupId.isPresent() || !groupId.get().isV2()) {
      Log.w(TAG, "Invalid group for group call update message");
      return;
    }

    RecipientId groupRecipientId = DatabaseFactory.getRecipientDatabase(context).getOrInsertFromPossiblyMigratedGroupId(groupId.get());

    DatabaseFactory.getSmsDatabase(context).insertOrUpdateGroupCall(groupRecipientId,
                                                                    senderRecipient.getId(),
                                                                    content.getServerReceivedTimestamp(),
                                                                    message.getGroupCallUpdate().get().getEraId());

    GroupCallPeekJob.enqueue(groupRecipientId);
  }

  private @Nullable MessageId handleEndSessionMessage(@NonNull SignalServiceContent content,
                                                      @NonNull Optional<Long> smsMessageId,
                                                      @NonNull Recipient senderRecipient)
  {
    MessageDatabase smsDatabase = DatabaseFactory.getSmsDatabase(context);
    IncomingTextMessage incomingTextMessage = new IncomingTextMessage(senderRecipient.getId(),
                                                                      content.getSenderDevice(),
                                                                      content.getTimestamp(),
                                                                      content.getServerReceivedTimestamp(),
                                                                      System.currentTimeMillis(),
                                                                      "",
                                                                      Optional.absent(),
                                                                      0,
                                                                      content.isNeedsReceipt(),
                                                                      content.getServerUuid());

    Optional<InsertResult> insertResult;

    if (!smsMessageId.isPresent()) {
      IncomingEndSessionMessage incomingEndSessionMessage = new IncomingEndSessionMessage(incomingTextMessage);
      insertResult = smsDatabase.insertMessageInbox(incomingEndSessionMessage);
    } else {
      smsDatabase.markAsEndSession(smsMessageId.get());
      insertResult = Optional.of(new InsertResult(smsMessageId.get(), smsDatabase.getThreadIdForMessage(smsMessageId.get())));
    }

    if (insertResult.isPresent()) {
      ApplicationDependencies.getSessionStore().deleteAllSessions(content.getSender().getIdentifier());

      SecurityEvent.broadcastSecurityUpdateEvent(context);
      ApplicationDependencies.getMessageNotifier().updateNotification(context, insertResult.get().getThreadId());

      return new MessageId(insertResult.get().getMessageId(), true);
    } else {
      return null;
    }
  }

  private long handleSynchronizeSentEndSessionMessage(@NonNull SentTranscriptMessage message)
      throws BadGroupIdException
  {
    MessageDatabase           database                  = DatabaseFactory.getSmsDatabase(context);
    Recipient                 recipient                 = getSyncMessageDestination(message);
    OutgoingTextMessage       outgoingTextMessage       = new OutgoingTextMessage(recipient, "", -1);
    OutgoingEndSessionMessage outgoingEndSessionMessage = new OutgoingEndSessionMessage(outgoingTextMessage);

    long threadId = DatabaseFactory.getThreadDatabase(context).getOrCreateThreadIdFor(recipient);

    if (!recipient.isGroup()) {
      ApplicationDependencies.getSessionStore().deleteAllSessions(recipient.requireServiceId());

      SecurityEvent.broadcastSecurityUpdateEvent(context);

      long messageId = database.insertMessageOutbox(threadId,
                                                    outgoingEndSessionMessage,
                                                    false,
                                                    message.getTimestamp(),
                                                    null);
      database.markAsSent(messageId, true);
      DatabaseFactory.getThreadDatabase(context).update(threadId, true);
    }

    return threadId;
  }

  private void handleGroupV1Message(@NonNull SignalServiceContent content,
                                    @NonNull SignalServiceDataMessage message,
                                    @NonNull Optional<Long> smsMessageId,
                                    @NonNull GroupId.V1 groupId,
                                    @NonNull Recipient senderRecipient,
                                    @NonNull Recipient threadRecipient,
                                    long receivedTime)
      throws StorageFailedException, BadGroupIdException
  {
    GroupV1MessageProcessor.process(context, content, message, false);

    if (message.getExpiresInSeconds() != 0 && message.getExpiresInSeconds() != threadRecipient.getExpiresInSeconds()) {
      handleExpirationUpdate(content, message, Optional.absent(), Optional.of(groupId), senderRecipient, threadRecipient, receivedTime);
    }

    if (smsMessageId.isPresent()) {
      DatabaseFactory.getSmsDatabase(context).deleteMessage(smsMessageId.get());
    }
  }

  private void handleUnknownGroupMessage(@NonNull SignalServiceContent content,
                                         @NonNull SignalServiceGroupContext group,
                                         @NonNull Recipient senderRecipient)
      throws BadGroupIdException
  {
    if (group.getGroupV1().isPresent()) {
      SignalServiceGroup groupV1 = group.getGroupV1().get();
      if (groupV1.getType() != SignalServiceGroup.Type.REQUEST_INFO) {
        ApplicationDependencies.getJobManager().add(new RequestGroupInfoJob(senderRecipient.getId(), GroupId.v1(groupV1.getGroupId())));
      } else {
        warn(content.getTimestamp(), "Received a REQUEST_INFO message for a group we don't know about. Ignoring.");
      }
    } else if (group.getGroupV2().isPresent()) {
      warn(content.getTimestamp(), "Received a GV2 message for a group we have no knowledge of -- attempting to fix this state.");
      DatabaseFactory.getGroupDatabase(context).fixMissingMasterKey(group.getGroupV2().get().getMasterKey());
    } else {
      warn(content.getTimestamp(), "Received a message for a group we don't know about without a group context. Ignoring.");
    }
  }

  private @Nullable MessageId handleExpirationUpdate(@NonNull SignalServiceContent content,
                                                     @NonNull SignalServiceDataMessage message,
                                                     @NonNull Optional<Long> smsMessageId,
                                                     @NonNull Optional<GroupId> groupId,
                                                     @NonNull Recipient senderRecipient,
                                                     @NonNull Recipient threadRecipient,
                                                     long receivedTime)
      throws StorageFailedException
  {
    if (groupId.isPresent() && groupId.get().isV2()) {
      warn(String.valueOf(content.getTimestamp()), "Expiration update received for GV2. Ignoring.");
      return null;
    }

    int                                 expiresInSeconds = message.getExpiresInSeconds();
    Optional<SignalServiceGroupContext> groupContext     = message.getGroupContext();
    if (threadRecipient.getExpiresInSeconds() == expiresInSeconds) {
      log(String.valueOf(content.getTimestamp()), "No change in message expiry for group. Ignoring.");
      return null;
    }

    try {
      MessageDatabase database = DatabaseFactory.getMmsDatabase(context);
      IncomingMediaMessage mediaMessage = new IncomingMediaMessage(senderRecipient.getId(),
                                                                   content.getTimestamp(),
                                                                   content.getServerReceivedTimestamp(),
                                                                   receivedTime,
                                                                   -1,
                                                                   expiresInSeconds * 1000L,
                                                                   true,
                                                                   false,
                                                                   content.isNeedsReceipt(),
                                                                   Optional.absent(),
                                                                   groupContext,
                                                                   Optional.absent(),
                                                                   Optional.absent(),
                                                                   Optional.absent(),
                                                                   Optional.absent(),
                                                                   Optional.absent(),
                                                                   Optional.absent(),
                                                                   content.getServerUuid());

      Optional<InsertResult> insertResult = database.insertSecureDecryptedMessageInbox(mediaMessage, -1);

      DatabaseFactory.getRecipientDatabase(context).setExpireMessages(threadRecipient.getId(), expiresInSeconds);

      if (smsMessageId.isPresent()) {
        DatabaseFactory.getSmsDatabase(context).deleteMessage(smsMessageId.get());
      }

      if (insertResult.isPresent()) {
        return new MessageId(insertResult.get().getMessageId(), true);
      }
    } catch (MmsException e) {
      throw new StorageFailedException(e, content.getSender().getIdentifier(), content.getSenderDevice());
    }

    return null;
  }

  private @Nullable MessageId handleReaction(@NonNull SignalServiceContent content, @NonNull SignalServiceDataMessage message, @NonNull Recipient senderRecipient) {
    SignalServiceDataMessage.Reaction reaction = message.getReaction().get();

    if (!EmojiUtil.isEmoji(reaction.getEmoji())) {
      Log.w(TAG, "Reaction text is not a valid emoji! Ignoring the message.");
      return null;
    }

    Recipient     targetAuthor  = Recipient.externalPush(context, reaction.getTargetAuthor());
    MessageRecord targetMessage = DatabaseFactory.getMmsSmsDatabase(context).getMessageFor(reaction.getTargetSentTimestamp(), targetAuthor.getId());

    if (targetMessage == null) {
      warn(String.valueOf(content.getTimestamp()), "[handleReaction] Could not find matching message! Putting it in the early message cache. timestamp: " + reaction.getTargetSentTimestamp() + "  author: " + targetAuthor.getId());
      ApplicationDependencies.getEarlyMessageCache().store(targetAuthor.getId(), reaction.getTargetSentTimestamp(), content);
      return null;
    }

    if (targetMessage.isRemoteDelete()) {
      warn(String.valueOf(content.getTimestamp()), "[handleReaction] Found a matching message, but it's flagged as remotely deleted. timestamp: " + reaction.getTargetSentTimestamp() + "  author: " + targetAuthor.getId());
      return null;
    }

    ThreadRecord targetThread = DatabaseFactory.getThreadDatabase(context).getThreadRecord(targetMessage.getThreadId());

    if (targetThread == null) {
      warn(String.valueOf(content.getTimestamp()), "[handleReaction] Could not find a thread for the message! timestamp: " + reaction.getTargetSentTimestamp() + "  author: " + targetAuthor.getId());
      return null;
    }

    Recipient threadRecipient = targetThread.getRecipient().resolve();

    if (threadRecipient.isGroup() && !threadRecipient.getParticipants().contains(senderRecipient)) {
      warn(String.valueOf(content.getTimestamp()), "[handleReaction] Reaction author is not in the group! timestamp: " + reaction.getTargetSentTimestamp() + "  author: " + targetAuthor.getId());
      return null;
    }

    if (!threadRecipient.isGroup() && !senderRecipient.equals(threadRecipient) && !senderRecipient.isSelf()) {
      warn(String.valueOf(content.getTimestamp()), "[handleReaction] Reaction author is not a part of the 1:1 thread! timestamp: " + reaction.getTargetSentTimestamp() + "  author: " + targetAuthor.getId());
      return null;
    }

    MessageDatabase db = targetMessage.isMms() ? DatabaseFactory.getMmsDatabase(context) : DatabaseFactory.getSmsDatabase(context);

    if (reaction.isRemove()) {
      db.deleteReaction(targetMessage.getId(), senderRecipient.getId());
      ApplicationDependencies.getMessageNotifier().updateNotification(context);
    } else {
      ReactionRecord reactionRecord = new ReactionRecord(reaction.getEmoji(), senderRecipient.getId(), message.getTimestamp(), System.currentTimeMillis());
      db.addReaction(targetMessage.getId(), reactionRecord);
      ApplicationDependencies.getMessageNotifier().updateNotification(context, targetMessage.getThreadId(), false);
    }

    return new MessageId(targetMessage.getId(), targetMessage.isMms());
  }

  private @Nullable MessageId handleRemoteDelete(@NonNull SignalServiceContent content, @NonNull SignalServiceDataMessage message, @NonNull Recipient senderRecipient) {
    SignalServiceDataMessage.RemoteDelete delete = message.getRemoteDelete().get();

    MessageRecord targetMessage = DatabaseFactory.getMmsSmsDatabase(context).getMessageFor(delete.getTargetSentTimestamp(), senderRecipient.getId());

    if (targetMessage != null && RemoteDeleteUtil.isValidReceive(targetMessage, senderRecipient, content.getServerReceivedTimestamp())) {
      MessageDatabase db = targetMessage.isMms() ? DatabaseFactory.getMmsDatabase(context) : DatabaseFactory.getSmsDatabase(context);
      db.markAsRemoteDelete(targetMessage.getId());
      ApplicationDependencies.getMessageNotifier().updateNotification(context, targetMessage.getThreadId(), false);
      return new MessageId(targetMessage.getId(), targetMessage.isMms());
    } else if (targetMessage == null) {
      warn(String.valueOf(content.getTimestamp()), "[handleRemoteDelete] Could not find matching message! timestamp: " + delete.getTargetSentTimestamp() + "  author: " + senderRecipient.getId());
      ApplicationDependencies.getEarlyMessageCache().store(senderRecipient.getId(), delete.getTargetSentTimestamp(), content);
      return null;
    } else {
      warn(String.valueOf(content.getTimestamp()), String.format(Locale.ENGLISH, "[handleRemoteDelete] Invalid remote delete! deleteTime: %d, targetTime: %d, deleteAuthor: %s, targetAuthor: %s",
                                                                 content.getServerReceivedTimestamp(), targetMessage.getServerTimestamp(), senderRecipient.getId(), targetMessage.getRecipient().getId()));
      return null;
    }
  }

  private void handleSynchronizeVerifiedMessage(@NonNull VerifiedMessage verifiedMessage) {
    IdentityUtil.processVerifiedMessage(context, verifiedMessage);
  }

  private void handleSynchronizeStickerPackOperation(@NonNull List<StickerPackOperationMessage> stickerPackOperations) {
    JobManager jobManager = ApplicationDependencies.getJobManager();

    for (StickerPackOperationMessage operation : stickerPackOperations) {
      if (operation.getPackId().isPresent() && operation.getPackKey().isPresent() && operation.getType().isPresent()) {
        String packId  = Hex.toStringCondensed(operation.getPackId().get());
        String packKey = Hex.toStringCondensed(operation.getPackKey().get());

        switch (operation.getType().get()) {
          case INSTALL:
            jobManager.add(StickerPackDownloadJob.forInstall(packId, packKey, false));
            break;
          case REMOVE:
            DatabaseFactory.getStickerDatabase(context).uninstallPack(packId);
            break;
        }
      } else {
        warn("Received incomplete sticker pack operation sync.");
      }
    }
  }

  private void handleSynchronizeConfigurationMessage(@NonNull ConfigurationMessage configurationMessage) {
    if (configurationMessage.getReadReceipts().isPresent()) {
      TextSecurePreferences.setReadReceiptsEnabled(context, configurationMessage.getReadReceipts().get());
    }

    if (configurationMessage.getUnidentifiedDeliveryIndicators().isPresent()) {
      TextSecurePreferences.setShowUnidentifiedDeliveryIndicatorsEnabled(context, configurationMessage.getReadReceipts().get());
    }

    if (configurationMessage.getTypingIndicators().isPresent()) {
      TextSecurePreferences.setTypingIndicatorsEnabled(context, configurationMessage.getTypingIndicators().get());
    }

    if (configurationMessage.getLinkPreviews().isPresent()) {
      SignalStore.settings().setLinkPreviewsEnabled(configurationMessage.getReadReceipts().get());
    }
  }

  private void handleSynchronizeBlockedListMessage(@NonNull BlockedListMessage blockMessage) {
    DatabaseFactory.getRecipientDatabase(context).applyBlockedUpdate(blockMessage.getAddresses(), blockMessage.getGroupIds());
  }

  private void handleSynchronizeFetchMessage(@NonNull SignalServiceSyncMessage.FetchType fetchType) {
    log("Received fetch request with type: " + fetchType);

    switch (fetchType) {
      case LOCAL_PROFILE:
        ApplicationDependencies.getJobManager().add(new RefreshOwnProfileJob());
        break;
      case STORAGE_MANIFEST:
        // StorageSyncHelper.scheduleSyncForDataChange();
        break;
      default:
        warn(TAG, "Received a fetch message for an unknown type.");
    }
  }

  private void handleSynchronizeMessageRequestResponse(@NonNull MessageRequestResponseMessage response)
      throws BadGroupIdException
  {
    RecipientDatabase recipientDatabase = DatabaseFactory.getRecipientDatabase(context);
    ThreadDatabase    threadDatabase    = DatabaseFactory.getThreadDatabase(context);

    Recipient recipient;

    if (response.getPerson().isPresent()) {
      recipient = Recipient.externalPush(context, response.getPerson().get());
    } else if (response.getGroupId().isPresent()) {
      GroupId groupId = GroupId.v1(response.getGroupId().get());
      recipient = Recipient.externalPossiblyMigratedGroup(context, groupId);
    } else {
      warn("Message request response was missing a thread recipient! Skipping.");
      return;
    }

    long threadId = threadDatabase.getOrCreateThreadIdFor(recipient);

    switch (response.getType()) {
      case ACCEPT:
        recipientDatabase.setProfileSharing(recipient.getId(), true);
        recipientDatabase.setBlocked(recipient.getId(), false);
        break;
      case DELETE:
        recipientDatabase.setProfileSharing(recipient.getId(), false);
        if (threadId > 0) threadDatabase.deleteConversation(threadId);
        break;
      case BLOCK:
        recipientDatabase.setBlocked(recipient.getId(), true);
        recipientDatabase.setProfileSharing(recipient.getId(), false);
        break;
      case BLOCK_AND_DELETE:
        recipientDatabase.setBlocked(recipient.getId(), true);
        recipientDatabase.setProfileSharing(recipient.getId(), false);
        if (threadId > 0) threadDatabase.deleteConversation(threadId);
        break;
      default:
        warn("Got an unknown response type! Skipping");
        break;
    }
  }

  private void handleSynchronizeOutgoingPayment(@NonNull SignalServiceContent content, @NonNull OutgoingPaymentMessage outgoingPaymentMessage) {
    RecipientId recipientId = outgoingPaymentMessage.getRecipient()
                                                    .transform(RecipientId::from)
                                                    .orNull();
    long timestamp = outgoingPaymentMessage.getBlockTimestamp();
    if (timestamp == 0) {
      timestamp = System.currentTimeMillis();
    }

    Optional<MobileCoinPublicAddress> address = outgoingPaymentMessage.getAddress().transform(MobileCoinPublicAddress::fromBytes);
    if (!address.isPresent() && recipientId == null) {
      log(content.getTimestamp(), "Inserting defrag");
      address     = Optional.of(ApplicationDependencies.getPayments().getWallet().getMobileCoinPublicAddress());
      recipientId = Recipient.self().getId();
    }

    UUID uuid = UUID.randomUUID();
    try {
      DatabaseFactory.getPaymentDatabase(context)
                     .createSuccessfulPayment(uuid,
                                              recipientId,
                                              address.get(),
                                              timestamp,
                                              outgoingPaymentMessage.getBlockIndex(),
                                              outgoingPaymentMessage.getNote().or(""),
                                              outgoingPaymentMessage.getAmount(),
                                              outgoingPaymentMessage.getFee(),
                                              outgoingPaymentMessage.getReceipt().toByteArray(),
                                              PaymentMetaDataUtil.fromKeysAndImages(outgoingPaymentMessage.getPublicKeys(), outgoingPaymentMessage.getKeyImages()));
    } catch (SerializationException e) {
      warn(content.getTimestamp(), "Ignoring synchronized outgoing payment with bad data.", e);
    }

    log("Inserted synchronized payment " + uuid);
  }

  private void handleSynchronizeSentMessage(@NonNull SignalServiceContent content,
                                            @NonNull SentTranscriptMessage message,
                                            @NonNull Recipient senderRecipient)
      throws StorageFailedException, BadGroupIdException, IOException, GroupChangeBusyException
  {
    log(String.valueOf(content.getTimestamp()), "Processing sent transcript for message with ID " + message.getTimestamp());

    try {
      GroupDatabase groupDatabase = DatabaseFactory.getGroupDatabase(context);

      if (message.getMessage().isGroupV2Message()) {
        GroupId.V2 groupId = GroupId.v2(message.getMessage().getGroupContext().get().getGroupV2().get().getMasterKey());
        if (handleGv2PreProcessing(groupId, content, message.getMessage().getGroupContext().get().getGroupV2().get(), senderRecipient)) {
          return;
        }
      }

      long threadId = -1;

      if (message.isRecipientUpdate()) {
        handleGroupRecipientUpdate(message);
      } else if (message.getMessage().isEndSession()) {
        threadId = handleSynchronizeSentEndSessionMessage(message);
      } else if (message.getMessage().isGroupV1Update()) {
        Long gv1ThreadId = GroupV1MessageProcessor.process(context, content, message.getMessage(), true);
        threadId = gv1ThreadId == null ? -1 : gv1ThreadId;
      } else if (message.getMessage().isGroupV2Update()) {
        handleSynchronizeSentGv2Update(content, message);
        threadId = DatabaseFactory.getThreadDatabase(context).getOrCreateThreadIdFor(getSyncMessageDestination(message));
      } else if (message.getMessage().getGroupCallUpdate().isPresent()) {
        handleGroupCallUpdateMessage(content, message.getMessage(), GroupUtil.idFromGroupContext(message.getMessage().getGroupContext()), senderRecipient);
      } else if (message.getMessage().isEmptyGroupV2Message()) {
        warn(content.getTimestamp(), "Empty GV2 message! Doing nothing.");
      } else if (message.getMessage().isExpirationUpdate()) {
        threadId = handleSynchronizeSentExpirationUpdate(message);
      } else if (message.getMessage().getReaction().isPresent()) {
        handleReaction(content, message.getMessage(), senderRecipient);
        threadId = DatabaseFactory.getThreadDatabase(context).getOrCreateThreadIdFor(getSyncMessageDestination(message));
      } else if (message.getMessage().getRemoteDelete().isPresent()) {
        handleRemoteDelete(content, message.getMessage(), senderRecipient);
      } else if (message.getMessage().getAttachments().isPresent() || message.getMessage().getQuote().isPresent() || message.getMessage().getPreviews().isPresent() || message.getMessage().getSticker().isPresent() || message.getMessage()
                                                                                                                                                                                                                               .isViewOnce() || message
                     .getMessage().getMentions().isPresent())
      {
        threadId = handleSynchronizeSentMediaMessage(message);
      } else {
        threadId = handleSynchronizeSentTextMessage(message);
      }

      if (message.getMessage().getGroupContext().isPresent() && groupDatabase.isUnknownGroup(GroupUtil.idFromGroupContext(message.getMessage().getGroupContext().get()))) {
        handleUnknownGroupMessage(content, message.getMessage().getGroupContext().get(), senderRecipient);
      }

      if (message.getMessage().getProfileKey().isPresent()) {
        Recipient recipient = getSyncMessageDestination(message);

        if (recipient != null && !recipient.isSystemContact() && !recipient.isProfileSharing()) {
          DatabaseFactory.getRecipientDatabase(context).setProfileSharing(recipient.getId(), true);
        }
      }

      if (threadId != -1) {
        DatabaseFactory.getThreadDatabase(context).setRead(threadId, true);
        ApplicationDependencies.getMessageNotifier().updateNotification(context);
      }

      if (SignalStore.rateLimit().needsRecaptcha()) {
        log(content.getTimestamp(), "Got a sent transcript while in reCAPTCHA mode. Assuming we're good to message again.");
        RateLimitUtil.retryAllRateLimitedMessages(context);
      }

      ApplicationDependencies.getMessageNotifier().setLastDesktopActivityTimestamp(message.getTimestamp());
    } catch (MmsException e) {
      throw new StorageFailedException(e, content.getSender().getIdentifier(), content.getSenderDevice());
    }
  }

  private void handleSynchronizeSentGv2Update(@NonNull SignalServiceContent content,
                                              @NonNull SentTranscriptMessage message)
      throws IOException, GroupChangeBusyException
  {
    SignalServiceGroupV2 signalServiceGroupV2 = message.getMessage().getGroupContext().get().getGroupV2().get();
    GroupId.V2           groupIdV2            = GroupId.v2(signalServiceGroupV2.getMasterKey());

    if (!updateGv2GroupFromServerOrP2PChange(content, signalServiceGroupV2)) {
      log(String.valueOf(content.getTimestamp()), "Ignoring GV2 message for group we are not currently in " + groupIdV2);
    }
  }

  private void handleSynchronizeRequestMessage(@NonNull RequestMessage message)
  {
    if (message.isContactsRequest()) {
      ApplicationDependencies.getJobManager().add(new MultiDeviceContactUpdateJob(true));
    }

    if (message.isGroupsRequest()) {
      ApplicationDependencies.getJobManager().add(new MultiDeviceGroupUpdateJob());
    }

    if (message.isBlockedListRequest()) {
      ApplicationDependencies.getJobManager().add(new MultiDeviceBlockedUpdateJob());
    }

    if (message.isConfigurationRequest()) {
      ApplicationDependencies.getJobManager().add(new MultiDeviceConfigurationUpdateJob(TextSecurePreferences.isReadReceiptsEnabled(context),
                                                                                        TextSecurePreferences.isTypingIndicatorsEnabled(context),
                                                                                        TextSecurePreferences.isShowUnidentifiedDeliveryIndicatorsEnabled(context),
                                                                                        SignalStore.settings().isLinkPreviewsEnabled()));
      ApplicationDependencies.getJobManager().add(new MultiDeviceStickerPackSyncJob());
    }

    if (message.isKeysRequest()) {
      ApplicationDependencies.getJobManager().add(new MultiDeviceKeysUpdateJob());
    }
  }

  private void handleSynchronizeReadMessage(@NonNull List<ReadMessage> readMessages, long envelopeTimestamp, @NonNull Recipient senderRecipient)
  {
    Map<Long, Long> threadToLatestRead = new HashMap<>();
    for (ReadMessage readMessage : readMessages) {
      List<Pair<Long, Long>> expiringText = DatabaseFactory.getSmsDatabase(context).setTimestampRead(new SyncMessageId(senderRecipient.getId(), readMessage.getTimestamp()),
                                                                                                     envelopeTimestamp,
                                                                                                     threadToLatestRead);
      List<Pair<Long, Long>> expiringMedia = DatabaseFactory.getMmsDatabase(context).setTimestampRead(new SyncMessageId(senderRecipient.getId(), readMessage.getTimestamp()),
                                                                                                      envelopeTimestamp,
                                                                                                      threadToLatestRead);

      for (Pair<Long, Long> expiringMessage : expiringText) {
        ApplicationDependencies.getExpiringMessageManager()
                               .scheduleDeletion(expiringMessage.first(), false, envelopeTimestamp, expiringMessage.second());
      }

      for (Pair<Long, Long> expiringMessage : expiringMedia) {
        ApplicationDependencies.getExpiringMessageManager()
                               .scheduleDeletion(expiringMessage.first(), true, envelopeTimestamp, expiringMessage.second());
      }
    }

    List<MessageDatabase.MarkedMessageInfo> markedMessages = DatabaseFactory.getThreadDatabase(context).setReadSince(threadToLatestRead, false);
    if (Util.hasItems(markedMessages)) {
      Log.i(TAG, "Updating past messages: " + markedMessages.size());
      MarkReadReceiver.process(context, markedMessages);
    }

    MessageNotifier messageNotifier = ApplicationDependencies.getMessageNotifier();
    messageNotifier.setLastDesktopActivityTimestamp(envelopeTimestamp);
    messageNotifier.cancelDelayedNotifications();
    messageNotifier.updateNotification(context);
  }

  private void handleSynchronizeViewedMessage(@NonNull List<ViewedMessage> viewedMessages, long envelopeTimestamp) {
    List<Long> toMarkViewed = Stream.of(viewedMessages)
                                    .map(message -> {
                                      RecipientId author = Recipient.externalPush(context, message.getSender()).getId();
                                      return DatabaseFactory.getMmsSmsDatabase(context).getMessageFor(message.getTimestamp(), author);
                                    })
                                    .filter(message -> message != null && message.isMms())
                                    .map(MessageRecord::getId)
                                    .toList();

    DatabaseFactory.getMmsDatabase(context).setIncomingMessagesViewed(toMarkViewed);

    MessageNotifier messageNotifier = ApplicationDependencies.getMessageNotifier();
    messageNotifier.setLastDesktopActivityTimestamp(envelopeTimestamp);
    messageNotifier.cancelDelayedNotifications();
    messageNotifier.updateNotification(context);
  }

  private void handleSynchronizeViewOnceOpenMessage(@NonNull ViewOnceOpenMessage openMessage, long envelopeTimestamp) {
    log(String.valueOf(envelopeTimestamp), "Handling a view-once open for message: " + openMessage.getTimestamp());

    RecipientId   author    = Recipient.externalPush(context, openMessage.getSender()).getId();
    long          timestamp = openMessage.getTimestamp();
    MessageRecord record    = DatabaseFactory.getMmsSmsDatabase(context).getMessageFor(timestamp, author);

    if (record != null && record.isMms()) {
      DatabaseFactory.getAttachmentDatabase(context).deleteAttachmentFilesForViewOnceMessage(record.getId());
    } else {
      warn(String.valueOf(envelopeTimestamp), "Got a view-once open message for a message we don't have!");
    }

    MessageNotifier messageNotifier = ApplicationDependencies.getMessageNotifier();
    messageNotifier.setLastDesktopActivityTimestamp(envelopeTimestamp);
    messageNotifier.cancelDelayedNotifications();
    messageNotifier.updateNotification(context);
  }

  private @Nullable MessageId handleMediaMessage(@NonNull SignalServiceContent content,
                                                 @NonNull SignalServiceDataMessage message,
                                                 @NonNull Optional<Long> smsMessageId,
                                                 @NonNull Recipient senderRecipient,
                                                 @NonNull Recipient threadRecipient,
                                                 long receivedTime)
      throws StorageFailedException
  {
    notifyTypingStoppedFromIncomingMessage(senderRecipient, threadRecipient, content.getSenderDevice());

    Optional<InsertResult> insertResult;

    MessageDatabase database = DatabaseFactory.getMmsDatabase(context);
    database.beginTransaction();

    try {
      Optional<QuoteModel>        quote          = getValidatedQuote(message.getQuote());
      Optional<List<Contact>>     sharedContacts = getContacts(message.getSharedContacts());
      Optional<List<LinkPreview>> linkPreviews   = getLinkPreviews(message.getPreviews(), message.getBody().or(""));
      Optional<List<Mention>>     mentions       = getMentions(message.getMentions());
      Optional<Attachment>        sticker        = getStickerAttachment(message.getSticker());
      IncomingMediaMessage mediaMessage = new IncomingMediaMessage(senderRecipient.getId(),
                                                                   message.getTimestamp(),
                                                                   content.getServerReceivedTimestamp(),
                                                                   receivedTime,
                                                                   -1,
                                                                   TimeUnit.SECONDS.toMillis(message.getExpiresInSeconds()),
                                                                   false,
                                                                   message.isViewOnce(),
                                                                   content.isNeedsReceipt(),
                                                                   message.getBody(),
                                                                   message.getGroupContext(),
                                                                   message.getAttachments(),
                                                                   quote,
                                                                   sharedContacts,
                                                                   linkPreviews,
                                                                   mentions,
                                                                   sticker,
                                                                   content.getServerUuid());

      insertResult = database.insertSecureDecryptedMessageInbox(mediaMessage, -1);

      if (insertResult.isPresent()) {
        if (smsMessageId.isPresent()) {
          DatabaseFactory.getSmsDatabase(context).deleteMessage(smsMessageId.get());
        }

        database.setTransactionSuccessful();
      }
    } catch (MmsException e) {
      throw new StorageFailedException(e, content.getSender().getIdentifier(), content.getSenderDevice());
    } finally {
      database.endTransaction();
    }

    if (insertResult.isPresent()) {
      List<DatabaseAttachment> allAttachments     = DatabaseFactory.getAttachmentDatabase(context).getAttachmentsForMessage(insertResult.get().getMessageId());
      List<DatabaseAttachment> stickerAttachments = Stream.of(allAttachments).filter(Attachment::isSticker).toList();
      List<DatabaseAttachment> attachments        = Stream.of(allAttachments).filterNot(Attachment::isSticker).toList();

      forceStickerDownloadIfNecessary(insertResult.get().getMessageId(), stickerAttachments);

      for (DatabaseAttachment attachment : attachments) {
        ApplicationDependencies.getJobManager().add(new AttachmentDownloadJob(insertResult.get().getMessageId(), attachment.getAttachmentId(), false));
      }

      ApplicationDependencies.getMessageNotifier().updateNotification(context, insertResult.get().getThreadId());
      TrimThreadJob.enqueueAsync(insertResult.get().getThreadId());

      if (message.isViewOnce()) {
        ApplicationDependencies.getViewOnceMessageManager().scheduleIfNecessary();
      }

      return new MessageId(insertResult.get().getMessageId(), true);
    } else {
      return null;
    }
  }

  private long handleSynchronizeSentExpirationUpdate(@NonNull SentTranscriptMessage message)
      throws MmsException, BadGroupIdException
  {
    MessageDatabase database  = DatabaseFactory.getMmsDatabase(context);
    Recipient       recipient = getSyncMessageDestination(message);

    OutgoingExpirationUpdateMessage expirationUpdateMessage = new OutgoingExpirationUpdateMessage(recipient,
                                                                                                  message.getTimestamp(),
                                                                                                  TimeUnit.SECONDS.toMillis(message.getMessage().getExpiresInSeconds()));

    long threadId  = DatabaseFactory.getThreadDatabase(context).getOrCreateThreadIdFor(recipient);
    long messageId = database.insertMessageOutbox(expirationUpdateMessage, threadId, false, null);

    database.markAsSent(messageId, true);

    DatabaseFactory.getRecipientDatabase(context).setExpireMessages(recipient.getId(), message.getMessage().getExpiresInSeconds());

    return threadId;
  }

  private long handleSynchronizeSentMediaMessage(@NonNull SentTranscriptMessage message)
      throws MmsException, BadGroupIdException
  {
    MessageDatabase             database       = DatabaseFactory.getMmsDatabase(context);
    Recipient                   recipients     = getSyncMessageDestination(message);
    Optional<QuoteModel>        quote          = getValidatedQuote(message.getMessage().getQuote());
    Optional<Attachment>        sticker        = getStickerAttachment(message.getMessage().getSticker());
    Optional<List<Contact>>     sharedContacts = getContacts(message.getMessage().getSharedContacts());
    Optional<List<LinkPreview>> previews       = getLinkPreviews(message.getMessage().getPreviews(), message.getMessage().getBody().or(""));
    Optional<List<Mention>>     mentions       = getMentions(message.getMessage().getMentions());
    boolean                     viewOnce       = message.getMessage().isViewOnce();
    List<Attachment> syncAttachments = viewOnce ? Collections.singletonList(new TombstoneAttachment(MediaUtil.VIEW_ONCE, false))
                                                : PointerAttachment.forPointers(message.getMessage().getAttachments());

    if (sticker.isPresent()) {
      syncAttachments.add(sticker.get());
    }

    OutgoingMediaMessage mediaMessage = new OutgoingMediaMessage(recipients, message.getMessage().getBody().orNull(),
                                                                 syncAttachments,
                                                                 message.getTimestamp(), -1,
                                                                 TimeUnit.SECONDS.toMillis(message.getMessage().getExpiresInSeconds()),
                                                                 viewOnce,
                                                                 ThreadDatabase.DistributionTypes.DEFAULT, quote.orNull(),
                                                                 sharedContacts.or(Collections.emptyList()),
                                                                 previews.or(Collections.emptyList()),
                                                                 mentions.or(Collections.emptyList()),
                                                                 Collections.emptySet(), Collections.emptySet());

    mediaMessage = new OutgoingSecureMediaMessage(mediaMessage);

    if (recipients.getExpiresInSeconds() != message.getMessage().getExpiresInSeconds()) {
      handleSynchronizeSentExpirationUpdate(message);
    }

    long threadId = DatabaseFactory.getThreadDatabase(context).getOrCreateThreadIdFor(recipients);

    long                     messageId;
    List<DatabaseAttachment> attachments;
    List<DatabaseAttachment> stickerAttachments;

    database.beginTransaction();

    try {
      messageId = database.insertMessageOutbox(mediaMessage, threadId, false, GroupReceiptDatabase.STATUS_UNKNOWN, null);

      if (recipients.isGroup()) {
        updateGroupReceiptStatus(message, messageId, recipients.requireGroupId());
      } else {
        database.markUnidentified(messageId, isUnidentified(message, recipients));
      }

      database.markAsSent(messageId, true);

      List<DatabaseAttachment> allAttachments = DatabaseFactory.getAttachmentDatabase(context).getAttachmentsForMessage(messageId);

      stickerAttachments = Stream.of(allAttachments).filter(Attachment::isSticker).toList();
      attachments        = Stream.of(allAttachments).filterNot(Attachment::isSticker).toList();

      if (message.getMessage().getExpiresInSeconds() > 0) {
        database.markExpireStarted(messageId, message.getExpirationStartTimestamp());
        ApplicationDependencies.getExpiringMessageManager()
                               .scheduleDeletion(messageId,
                                                 true,
                                                 message.getExpirationStartTimestamp(),
                                                 TimeUnit.SECONDS.toMillis(message.getMessage().getExpiresInSeconds()));
      }

      if (recipients.isSelf()) {
        SyncMessageId id = new SyncMessageId(recipients.getId(), message.getTimestamp());
        DatabaseFactory.getMmsSmsDatabase(context).incrementDeliveryReceiptCount(id, System.currentTimeMillis());
        DatabaseFactory.getMmsSmsDatabase(context).incrementReadReceiptCount(id, System.currentTimeMillis());
      }

      database.setTransactionSuccessful();
    } finally {
      database.endTransaction();
    }

    for (DatabaseAttachment attachment : attachments) {
      ApplicationDependencies.getJobManager().add(new AttachmentDownloadJob(messageId, attachment.getAttachmentId(), false));
    }

    forceStickerDownloadIfNecessary(messageId, stickerAttachments);

    return threadId;
  }

  private void handleGroupRecipientUpdate(@NonNull SentTranscriptMessage message)
      throws BadGroupIdException
  {
    Recipient recipient = getSyncMessageDestination(message);

    if (!recipient.isGroup()) {
      warn("Got recipient update for a non-group message! Skipping.");
      return;
    }

    MmsSmsDatabase database = DatabaseFactory.getMmsSmsDatabase(context);
    MessageRecord  record   = database.getMessageFor(message.getTimestamp(), Recipient.self().getId());

    if (record == null) {
      warn("Got recipient update for non-existing message! Skipping.");
      return;
    }

    if (!record.isMms()) {
      warn("Recipient update matched a non-MMS message! Skipping.");
      return;
    }

    updateGroupReceiptStatus(message, record.getId(), recipient.requireGroupId());
  }

  private void updateGroupReceiptStatus(@NonNull SentTranscriptMessage message, long messageId, @NonNull GroupId groupString) {
    GroupReceiptDatabase receiptDatabase     = DatabaseFactory.getGroupReceiptDatabase(context);
    List<RecipientId>    messageRecipientIds = Stream.of(message.getRecipients()).map(RecipientId::from).toList();
    List<Recipient>      members             = DatabaseFactory.getGroupDatabase(context).getGroupMembers(groupString, GroupDatabase.MemberSet.FULL_MEMBERS_EXCLUDING_SELF);
    Map<RecipientId, Integer> localReceipts = Stream.of(receiptDatabase.getGroupReceiptInfo(messageId))
                                                    .collect(Collectors.toMap(GroupReceiptInfo::getRecipientId, GroupReceiptInfo::getStatus));

    for (RecipientId messageRecipientId : messageRecipientIds) {
      //noinspection ConstantConditions
      if (localReceipts.containsKey(messageRecipientId) && localReceipts.get(messageRecipientId) < GroupReceiptDatabase.STATUS_UNDELIVERED) {
        receiptDatabase.update(messageRecipientId, messageId, GroupReceiptDatabase.STATUS_UNDELIVERED, message.getTimestamp());
      } else if (!localReceipts.containsKey(messageRecipientId)) {
        receiptDatabase.insert(Collections.singletonList(messageRecipientId), messageId, GroupReceiptDatabase.STATUS_UNDELIVERED, message.getTimestamp());
      }
    }

    List<org.whispersystems.libsignal.util.Pair<RecipientId, Boolean>> unidentifiedStatus = Stream.of(members)
                                                                                                  .map(m -> new org.whispersystems.libsignal.util.Pair<>(m.getId(), message.isUnidentified(m.requireServiceId())))
                                                                                                  .toList();
    receiptDatabase.setUnidentified(unidentifiedStatus, messageId);
  }

  private @Nullable MessageId handleTextMessage(@NonNull SignalServiceContent content,
                                                @NonNull SignalServiceDataMessage message,
                                                @NonNull Optional<Long> smsMessageId,
                                                @NonNull Optional<GroupId> groupId,
                                                @NonNull Recipient senderRecipient,
                                                @NonNull Recipient threadRecipient,
                                                long receivedTime)
      throws StorageFailedException
  {
    MessageDatabase database = DatabaseFactory.getSmsDatabase(context);
    String          body     = message.getBody().isPresent() ? message.getBody().get() : "";

    if (message.getExpiresInSeconds() != threadRecipient.getExpiresInSeconds()) {
      handleExpirationUpdate(content, message, Optional.absent(), groupId, senderRecipient, threadRecipient, receivedTime);
    }

    Optional<InsertResult> insertResult;

    if (smsMessageId.isPresent() && !message.getGroupContext().isPresent()) {
      insertResult = Optional.of(database.updateBundleMessageBody(smsMessageId.get(), body));
    } else {
      notifyTypingStoppedFromIncomingMessage(senderRecipient, threadRecipient, content.getSenderDevice());

      IncomingTextMessage textMessage = new IncomingTextMessage(senderRecipient.getId(),
                                                                content.getSenderDevice(),
                                                                message.getTimestamp(),
                                                                content.getServerReceivedTimestamp(),
                                                                receivedTime,
                                                                body,
                                                                groupId,
                                                                TimeUnit.SECONDS.toMillis(message.getExpiresInSeconds()),
                                                                content.isNeedsReceipt(),
                                                                content.getServerUuid());

      textMessage  = new IncomingEncryptedMessage(textMessage, body);
      insertResult = database.insertMessageInbox(textMessage);

      if (smsMessageId.isPresent()) database.deleteMessage(smsMessageId.get());
    }

    if (insertResult.isPresent()) {
      ApplicationDependencies.getMessageNotifier().updateNotification(context, insertResult.get().getThreadId());
      return new MessageId(insertResult.get().getMessageId(), false);
    } else {
      return null;
    }
  }

  private long handleSynchronizeSentTextMessage(@NonNull SentTranscriptMessage message)
      throws MmsException, BadGroupIdException
  {
    Recipient recipient       = getSyncMessageDestination(message);
    String    body            = message.getMessage().getBody().or("");
    long      expiresInMillis = TimeUnit.SECONDS.toMillis(message.getMessage().getExpiresInSeconds());

    if (recipient.getExpiresInSeconds() != message.getMessage().getExpiresInSeconds()) {
      handleSynchronizeSentExpirationUpdate(message);
    }

    long    threadId = DatabaseFactory.getThreadDatabase(context).getOrCreateThreadIdFor(recipient);
    boolean isGroup  = recipient.isGroup();

    MessageDatabase database;
    long            messageId;

    if (isGroup) {
      OutgoingMediaMessage outgoingMediaMessage = new OutgoingMediaMessage(recipient,
                                                                           new SlideDeck(),
                                                                           body,
                                                                           message.getTimestamp(),
                                                                           -1,
                                                                           expiresInMillis,
                                                                           false,
                                                                           ThreadDatabase.DistributionTypes.DEFAULT,
                                                                           null,
                                                                           Collections.emptyList(),
                                                                           Collections.emptyList(),
                                                                           Collections.emptyList());
      outgoingMediaMessage = new OutgoingSecureMediaMessage(outgoingMediaMessage);

      messageId = DatabaseFactory.getMmsDatabase(context).insertMessageOutbox(outgoingMediaMessage, threadId, false, GroupReceiptDatabase.STATUS_UNKNOWN, null);
      database  = DatabaseFactory.getMmsDatabase(context);

      updateGroupReceiptStatus(message, messageId, recipient.requireGroupId());
    } else {
      OutgoingTextMessage outgoingTextMessage = new OutgoingEncryptedMessage(recipient, body, expiresInMillis);

      messageId = DatabaseFactory.getSmsDatabase(context).insertMessageOutbox(threadId, outgoingTextMessage, false, message.getTimestamp(), null);
      database  = DatabaseFactory.getSmsDatabase(context);
      database.markUnidentified(messageId, isUnidentified(message, recipient));
      DatabaseFactory.getThreadDatabase(context).update(threadId, true);
    }

    database.markAsSent(messageId, true);

    if (expiresInMillis > 0) {
      database.markExpireStarted(messageId, message.getExpirationStartTimestamp());
      ApplicationDependencies.getExpiringMessageManager()
                             .scheduleDeletion(messageId, isGroup, message.getExpirationStartTimestamp(), expiresInMillis);
    }

    if (recipient.isSelf()) {
      SyncMessageId id = new SyncMessageId(recipient.getId(), message.getTimestamp());
      DatabaseFactory.getMmsSmsDatabase(context).incrementDeliveryReceiptCount(id, System.currentTimeMillis());
      DatabaseFactory.getMmsSmsDatabase(context).incrementReadReceiptCount(id, System.currentTimeMillis());
    }

    return threadId;
  }

  private void handleInvalidVersionMessage(@NonNull String sender, int senderDevice, long timestamp,
                                           @NonNull Optional<Long> smsMessageId)
  {
    MessageDatabase smsDatabase = DatabaseFactory.getSmsDatabase(context);

    if (!smsMessageId.isPresent()) {
      Optional<InsertResult> insertResult = insertPlaceholder(sender, senderDevice, timestamp);

      if (insertResult.isPresent()) {
        smsDatabase.markAsInvalidVersionKeyExchange(insertResult.get().getMessageId());
        ApplicationDependencies.getMessageNotifier().updateNotification(context, insertResult.get().getThreadId());
      }
    } else {
      smsDatabase.markAsInvalidVersionKeyExchange(smsMessageId.get());
    }
  }

  private void handleCorruptMessage(@NonNull String sender, int senderDevice, long timestamp,
                                    @NonNull Optional<Long> smsMessageId)
  {
    MessageDatabase smsDatabase = DatabaseFactory.getSmsDatabase(context);

    if (!smsMessageId.isPresent()) {
      Optional<InsertResult> insertResult = insertPlaceholder(sender, senderDevice, timestamp);

      if (insertResult.isPresent()) {
        smsDatabase.markAsDecryptFailed(insertResult.get().getMessageId());
        ApplicationDependencies.getMessageNotifier().updateNotification(context, insertResult.get().getThreadId());
      }
    } else {
      smsDatabase.markAsDecryptFailed(smsMessageId.get());
    }
  }

  private void handleUnsupportedDataMessage(@NonNull String sender,
                                            int senderDevice,
                                            @NonNull Optional<GroupId> groupId,
                                            long timestamp,
                                            @NonNull Optional<Long> smsMessageId)
  {
    MessageDatabase smsDatabase = DatabaseFactory.getSmsDatabase(context);

    if (!smsMessageId.isPresent()) {
      Optional<InsertResult> insertResult = insertPlaceholder(sender, senderDevice, timestamp, groupId);

      if (insertResult.isPresent()) {
        smsDatabase.markAsUnsupportedProtocolVersion(insertResult.get().getMessageId());
        ApplicationDependencies.getMessageNotifier().updateNotification(context, insertResult.get().getThreadId());
      }
    } else {
      smsDatabase.markAsNoSession(smsMessageId.get());
    }
  }

  private void handleInvalidMessage(@NonNull SignalServiceAddress sender,
                                    int senderDevice,
                                    @NonNull Optional<GroupId> groupId,
                                    long timestamp,
                                    @NonNull Optional<Long> smsMessageId)
  {
    MessageDatabase smsDatabase = DatabaseFactory.getSmsDatabase(context);

    if (!smsMessageId.isPresent()) {
      Optional<InsertResult> insertResult = insertPlaceholder(sender.getIdentifier(), senderDevice, timestamp, groupId);

      if (insertResult.isPresent()) {
        smsDatabase.markAsInvalidMessage(insertResult.get().getMessageId());
        ApplicationDependencies.getMessageNotifier().updateNotification(context, insertResult.get().getThreadId());
      }
    } else {
      smsDatabase.markAsNoSession(smsMessageId.get());
    }
  }

  private void handleLegacyMessage(@NonNull String sender, int senderDevice, long timestamp,
                                   @NonNull Optional<Long> smsMessageId)
  {
    MessageDatabase smsDatabase = DatabaseFactory.getSmsDatabase(context);

    if (!smsMessageId.isPresent()) {
      Optional<InsertResult> insertResult = insertPlaceholder(sender, senderDevice, timestamp);

      if (insertResult.isPresent()) {
        smsDatabase.markAsLegacyVersion(insertResult.get().getMessageId());
        ApplicationDependencies.getMessageNotifier().updateNotification(context, insertResult.get().getThreadId());
      }
    } else {
      smsDatabase.markAsLegacyVersion(smsMessageId.get());
    }
  }

  private void handleProfileKey(@NonNull SignalServiceContent content,
                                @NonNull byte[] messageProfileKeyBytes,
                                @NonNull Recipient senderRecipient)
  {
    RecipientDatabase database          = DatabaseFactory.getRecipientDatabase(context);
    ProfileKey        messageProfileKey = ProfileKeyUtil.profileKeyOrNull(messageProfileKeyBytes);

    if (messageProfileKey != null) {
      if (database.setProfileKey(senderRecipient.getId(), messageProfileKey)) {
        ApplicationDependencies.getJobManager().add(RetrieveProfileJob.forRecipient(senderRecipient.getId()));
      }
    } else {
      warn(String.valueOf(content.getTimestamp()), "Ignored invalid profile key seen in message");
    }
  }

  private void handleNeedsDeliveryReceipt(@NonNull SignalServiceContent content,
                                          @NonNull SignalServiceDataMessage message,
                                          @NonNull MessageId messageId)
  {
    ApplicationDependencies.getJobManager().add(new SendDeliveryReceiptJob(RecipientId.fromHighTrust(content.getSender()), message.getTimestamp(), messageId));
  }

  private void handleViewedReceipt(@NonNull SignalServiceContent content,
                                   @NonNull SignalServiceReceiptMessage message,
                                   @NonNull Recipient senderRecipient)
  {
    if (!TextSecurePreferences.isReadReceiptsEnabled(context)) {
      log("Ignoring viewed receipts for IDs: " + Util.join(message.getTimestamps(), ", "));
      return;
    }

    log(TAG, "Processing viewed receipts. Sender: " + senderRecipient.getId() + ", Device: " + content.getSenderDevice() + ", Timestamps: " + Util.join(message.getTimestamps(), ", "));

    List<SyncMessageId> ids = Stream.of(message.getTimestamps())
                                    .map(t -> new SyncMessageId(senderRecipient.getId(), t))
                                    .toList();

    Collection<SyncMessageId> unhandled = DatabaseFactory.getMmsSmsDatabase(context)
                                                         .incrementViewedReceiptCounts(ids, content.getTimestamp());

    for (SyncMessageId id : unhandled) {
      warn(String.valueOf(content.getTimestamp()), "[handleViewedReceipt] Could not find matching message! timestamp: " + id.getTimetamp() + "  author: " + senderRecipient.getId());
      ApplicationDependencies.getEarlyMessageCache().store(senderRecipient.getId(), id.getTimetamp(), content);
    }
  }

  @SuppressLint("DefaultLocale")
  private void handleDeliveryReceipt(@NonNull SignalServiceContent content,
                                     @NonNull SignalServiceReceiptMessage message,
                                     @NonNull Recipient senderRecipient)
  {
    log(TAG, "Processing delivery receipts. Sender: " + senderRecipient.getId() + ", Device: " + content.getSenderDevice() + ", Timestamps: " + Util.join(message.getTimestamps(), ", "));

    List<SyncMessageId> ids = Stream.of(message.getTimestamps())
                                    .map(t -> new SyncMessageId(senderRecipient.getId(), t))
                                    .toList();

    DatabaseFactory.getMmsSmsDatabase(context).incrementDeliveryReceiptCounts(ids, System.currentTimeMillis());
    DatabaseFactory.getMessageLogDatabase(context).deleteEntriesForRecipient(message.getTimestamps(), senderRecipient.getId(), content.getSenderDevice());
  }

  @SuppressLint("DefaultLocale")
  private void handleReadReceipt(@NonNull SignalServiceContent content,
                                 @NonNull SignalServiceReceiptMessage message,
                                 @NonNull Recipient senderRecipient)
  {
    if (!TextSecurePreferences.isReadReceiptsEnabled(context)) {
      log("Ignoring read receipts for IDs: " + Util.join(message.getTimestamps(), ", "));
      return;
    }

    log(TAG, "Processing read receipts. Sender: " + senderRecipient.getId() + ", Device: " + content.getSenderDevice() + ", Timestamps: " + Util.join(message.getTimestamps(), ", "));

    List<SyncMessageId> ids = Stream.of(message.getTimestamps())
                                    .map(t -> new SyncMessageId(senderRecipient.getId(), t))
                                    .toList();

    Collection<SyncMessageId> unhandled = DatabaseFactory.getMmsSmsDatabase(context).incrementReadReceiptCounts(ids, content.getTimestamp());

    for (SyncMessageId id : unhandled) {
      warn(String.valueOf(content.getTimestamp()), "[handleReadReceipt] Could not find matching message! timestamp: " + id.getTimetamp() + "  author: " + senderRecipient.getId());
      ApplicationDependencies.getEarlyMessageCache().store(senderRecipient.getId(), id.getTimetamp(), content);
    }
  }

  private void handleTypingMessage(@NonNull SignalServiceContent content,
                                   @NonNull SignalServiceTypingMessage typingMessage,
                                   @NonNull Recipient senderRecipient)
      throws BadGroupIdException
  {
    if (!TextSecurePreferences.isTypingIndicatorsEnabled(context)) {
      return;
    }

    long threadId;

    if (typingMessage.getGroupId().isPresent()) {
      GroupId.Push groupId = GroupId.push(typingMessage.getGroupId().get());

      if (!DatabaseFactory.getGroupDatabase(context).isCurrentMember(groupId, senderRecipient.getId())) {
        warn(String.valueOf(content.getTimestamp()), "Seen typing indicator for non-member " + senderRecipient.getId());
        return;
      }

      Recipient groupRecipient = Recipient.externalPossiblyMigratedGroup(context, groupId);

      threadId = DatabaseFactory.getThreadDatabase(context).getOrCreateThreadIdFor(groupRecipient);
    } else {
      threadId = DatabaseFactory.getThreadDatabase(context).getOrCreateThreadIdFor(senderRecipient);
    }

    if (threadId <= 0) {
      warn(String.valueOf(content.getTimestamp()), "Couldn't find a matching thread for a typing message.");
      return;
    }

    if (typingMessage.isTypingStarted()) {
      Log.d(TAG, "Typing started on thread " + threadId);
      ApplicationDependencies.getTypingStatusRepository().onTypingStarted(context, threadId, senderRecipient, content.getSenderDevice());
    } else {
      Log.d(TAG, "Typing stopped on thread " + threadId);
      ApplicationDependencies.getTypingStatusRepository().onTypingStopped(context, threadId, senderRecipient, content.getSenderDevice(), false);
    }
  }

  private void handleRetryReceipt(@NonNull SignalServiceContent content, @NonNull DecryptionErrorMessage decryptionErrorMessage, @NonNull Recipient senderRecipient) {
    if (!FeatureFlags.retryReceipts()) {
      warn(String.valueOf(content.getTimestamp()), "[RetryReceipt] Feature flag disabled, skipping retry receipt.");
      return;
    }

    long sentTimestamp = decryptionErrorMessage.getTimestamp();

    warn(content.getTimestamp(), "[RetryReceipt] Received a retry receipt from " + formatSender(senderRecipient, content) + " for message with timestamp " + sentTimestamp + ".");

    if (!senderRecipient.hasAci()) {
      warn(content.getTimestamp(), "[RetryReceipt] Requester " + senderRecipient.getId() + " somehow has no UUID! timestamp: " + sentTimestamp);
      return;
    }

    MessageLogEntry messageLogEntry = DatabaseFactory.getMessageLogDatabase(context).getLogEntry(senderRecipient.getId(), content.getSenderDevice(), sentTimestamp);

    if (decryptionErrorMessage.getRatchetKey().isPresent()) {
      handleIndividualRetryReceipt(senderRecipient, messageLogEntry, content, decryptionErrorMessage);
    } else {
      handleSenderKeyRetryReceipt(senderRecipient, messageLogEntry, content, decryptionErrorMessage);
    }
  }

  private void handleSenderKeyRetryReceipt(@NonNull Recipient requester,
                                           @Nullable MessageLogEntry messageLogEntry,
                                           @NonNull SignalServiceContent content,
                                           @NonNull DecryptionErrorMessage decryptionErrorMessage)
  {
    long          sentTimestamp  = decryptionErrorMessage.getTimestamp();
    MessageRecord relatedMessage = findRetryReceiptRelatedMessage(context, messageLogEntry, sentTimestamp);

    if (relatedMessage == null) {
      warn(content.getTimestamp(), "[RetryReceipt-SK] The related message could not be found! There shouldn't be any sender key resends where we can't find the related message. Skipping.");
      return;
    }

    Recipient threadRecipient = DatabaseFactory.getThreadDatabase(context).getRecipientForThreadId(relatedMessage.getThreadId());

    if (threadRecipient == null) {
      warn(content.getTimestamp(), "[RetryReceipt-SK] Could not find a thread recipient! Skipping.");
      return;
    }

    if (!threadRecipient.isPushV2Group()) {
      warn(content.getTimestamp(), "[RetryReceipt-SK] Thread recipient is not a v2 group! Skipping.");
      return;
    }

    GroupId.V2     groupId        = threadRecipient.requireGroupId().requireV2();
    DistributionId distributionId = DatabaseFactory.getGroupDatabase(context).getOrCreateDistributionId(groupId);

    SignalProtocolAddress requesterAddress = new SignalProtocolAddress(requester.requireAci().toString(), content.getSenderDevice());
    DatabaseFactory.getSenderKeySharedDatabase(context).delete(distributionId, Collections.singleton(requesterAddress));

    if (messageLogEntry != null) {
      warn(content.getTimestamp(), "[RetryReceipt-SK] Found MSL entry for " + requester.getId() + " (" + requesterAddress + ") with timestamp " + sentTimestamp + ". Scheduling a resend.");

      ApplicationDependencies.getJobManager().add(new ResendMessageJob(messageLogEntry.getRecipientId(),
                                                                       messageLogEntry.getDateSent(),
                                                                       messageLogEntry.getContent(),
                                                                       messageLogEntry.getContentHint(),
                                                                       groupId,
                                                                       distributionId));
    } else {
      warn(content.getTimestamp(), "[RetryReceipt-SK] Unable to find MSL entry for " + requester.getId() + " (" + requesterAddress + ") with timestamp " + sentTimestamp + ".");

      Optional<GroupRecord> groupRecord = DatabaseFactory.getGroupDatabase(context).getGroup(groupId);

      if (!groupRecord.isPresent()) {
        warn(content.getTimestamp(), "[RetryReceipt-SK] Could not find a record for the group!");
        return;
      }

      if (!groupRecord.get().getMembers().contains(requester.getId())) {
        warn(content.getTimestamp(), "[RetryReceipt-SK] The requester is not in the group, so we cannot send them a SenderKeyDistributionMessage.");
        return;
      }

      warn(content.getTimestamp(), "[RetryReceipt-SK] The requester is in the group, so we'll send them a SenderKeyDistributionMessage.");
      ApplicationDependencies.getJobManager().add(new SenderKeyDistributionSendJob(requester.getId(), groupRecord.get().getId().requireV2()));
    }
  }

  private void handleIndividualRetryReceipt(@NonNull Recipient requester, @Nullable MessageLogEntry messageLogEntry, @NonNull SignalServiceContent content, @NonNull DecryptionErrorMessage decryptionErrorMessage) {
    boolean archivedSession = false;

    if (decryptionErrorMessage.getDeviceId() == SignalServiceAddress.DEFAULT_DEVICE_ID &&
        decryptionErrorMessage.getRatchetKey().isPresent() &&
        SessionUtil.ratchetKeyMatches(requester, content.getSenderDevice(), decryptionErrorMessage.getRatchetKey().get()))
    {
      warn(content.getTimestamp(), "[RetryReceipt-I] Ratchet key matches. Archiving the session.");
      SessionUtil.archiveSession(requester.getId(), content.getSenderDevice());
      archivedSession = true;
    }

    if (messageLogEntry != null) {
      warn(content.getTimestamp(), "[RetryReceipt-I] Found an entry in the MSL. Resending.");
      ApplicationDependencies.getJobManager().add(new ResendMessageJob(messageLogEntry.getRecipientId(),
                                                                       messageLogEntry.getDateSent(),
                                                                       messageLogEntry.getContent(),
                                                                       messageLogEntry.getContentHint(),
                                                                       null,
                                                                       null));
    } else if (archivedSession) {
      warn(content.getTimestamp(), "[RetryReceipt-I] Could not find an entry in the MSL, but we archived the session, so we're sending a null message to complete the reset.");
      ApplicationDependencies.getJobManager().add(new NullMessageSendJob(requester.getId()));
    } else {
      warn(content.getTimestamp(), "[RetryReceipt-I] Could not find an entry in the MSL. Skipping.");
    }
  }

  private @Nullable MessageRecord findRetryReceiptRelatedMessage(@NonNull Context context, @Nullable MessageLogEntry messageLogEntry, long sentTimestamp) {
    if (messageLogEntry != null && messageLogEntry.hasRelatedMessage()) {
      MessageId relatedMessage = messageLogEntry.getRelatedMessages().get(0);

      if (relatedMessage.isMms()) {
        return DatabaseFactory.getMmsDatabase(context).getMessageRecordOrNull(relatedMessage.getId());
      } else {
        return DatabaseFactory.getSmsDatabase(context).getMessageRecordOrNull(relatedMessage.getId());
      }
    } else {
      return DatabaseFactory.getMmsSmsDatabase(context).getMessageFor(sentTimestamp, Recipient.self().getId());
    }
  }

  private static boolean isInvalidMessage(@NonNull SignalServiceDataMessage message) {
    if (message.isViewOnce()) {
      List<SignalServiceAttachment> attachments = message.getAttachments().or(Collections.emptyList());

      return attachments.size() != 1 ||
             !isViewOnceSupportedContentType(attachments.get(0).getContentType().toLowerCase());
    }

    return false;
  }

  private static boolean isViewOnceSupportedContentType(@NonNull String contentType) {
    return MediaUtil.isImageType(contentType) || MediaUtil.isVideoType(contentType);
  }

  private Optional<QuoteModel> getValidatedQuote(Optional<SignalServiceDataMessage.Quote> quote) {
    if (!quote.isPresent()) return Optional.absent();

    if (quote.get().getId() <= 0) {
      warn("Received quote without an ID! Ignoring...");
      return Optional.absent();
    }

    if (quote.get().getAuthor() == null) {
      warn("Received quote without an author! Ignoring...");
      return Optional.absent();
    }

    RecipientId   author  = Recipient.externalPush(context, quote.get().getAuthor()).getId();
    MessageRecord message = DatabaseFactory.getMmsSmsDatabase(context).getMessageFor(quote.get().getId(), author);

    if (message != null && !message.isRemoteDelete()) {
      log("Found matching message record...");

      List<Attachment> attachments = new LinkedList<>();
      List<Mention>    mentions    = new LinkedList<>();

      if (message.isMms()) {
        MmsMessageRecord mmsMessage = (MmsMessageRecord) message;

        mentions.addAll(DatabaseFactory.getMentionDatabase(context).getMentionsForMessage(mmsMessage.getId()));

        if (mmsMessage.isViewOnce()) {
          attachments.add(new TombstoneAttachment(MediaUtil.VIEW_ONCE, true));
        } else {
          attachments = mmsMessage.getSlideDeck().asAttachments();

          if (attachments.isEmpty()) {
            attachments.addAll(Stream.of(mmsMessage.getLinkPreviews())
                                     .filter(lp -> lp.getThumbnail().isPresent())
                                     .map(lp -> lp.getThumbnail().get())
                                     .toList());
          }
        }
      }

      return Optional.of(new QuoteModel(quote.get().getId(), author, message.getBody(), false, attachments, mentions));
    } else if (message != null) {
      warn("Found the target for the quote, but it's flagged as remotely deleted.");
    }

    warn("Didn't find matching message record...");

    return Optional.of(new QuoteModel(quote.get().getId(),
                                      author,
                                      quote.get().getText(),
                                      true,
                                      PointerAttachment.forPointers(quote.get().getAttachments()),
                                      getMentions(quote.get().getMentions())));
  }

  private Optional<Attachment> getStickerAttachment(Optional<SignalServiceDataMessage.Sticker> sticker) {
    if (!sticker.isPresent()) {
      return Optional.absent();
    }

    if (sticker.get().getPackId() == null || sticker.get().getPackKey() == null || sticker.get().getAttachment() == null) {
      warn("Malformed sticker!");
      return Optional.absent();
    }

    String          packId          = Hex.toStringCondensed(sticker.get().getPackId());
    String          packKey         = Hex.toStringCondensed(sticker.get().getPackKey());
    int             stickerId       = sticker.get().getStickerId();
    String          emoji           = sticker.get().getEmoji();
    StickerLocator  stickerLocator  = new StickerLocator(packId, packKey, stickerId, emoji);
    StickerDatabase stickerDatabase = DatabaseFactory.getStickerDatabase(context);
    StickerRecord   stickerRecord   = stickerDatabase.getSticker(stickerLocator.getPackId(), stickerLocator.getStickerId(), false);

    if (stickerRecord != null) {
      return Optional.of(new UriAttachment(stickerRecord.getUri(),
                                           stickerRecord.getContentType(),
                                           AttachmentDatabase.TRANSFER_PROGRESS_DONE,
                                           stickerRecord.getSize(),
                                           StickerSlide.WIDTH,
                                           StickerSlide.HEIGHT,
                                           null,
                                           String.valueOf(new SecureRandom().nextLong()),
                                           false,
                                           false,
                                           false,
                                           false,
                                           null,
                                           stickerLocator,
                                           null,
                                           null,
                                           null));
    } else {
      return Optional.of(PointerAttachment.forPointer(Optional.of(sticker.get().getAttachment()), stickerLocator).get());
    }
  }

  private static Optional<List<Contact>> getContacts(Optional<List<SharedContact>> sharedContacts) {
    if (!sharedContacts.isPresent()) return Optional.absent();

    List<Contact> contacts = new ArrayList<>(sharedContacts.get().size());

    for (SharedContact sharedContact : sharedContacts.get()) {
      contacts.add(ContactModelMapper.remoteToLocal(sharedContact));
    }

    return Optional.of(contacts);
  }

  private Optional<List<LinkPreview>> getLinkPreviews(Optional<List<SignalServiceDataMessage.Preview>> previews, @NonNull String message) {
    if (!previews.isPresent() || previews.get().isEmpty()) return Optional.absent();

    List<LinkPreview>     linkPreviews  = new ArrayList<>(previews.get().size());
    LinkPreviewUtil.Links urlsInMessage = LinkPreviewUtil.findValidPreviewUrls(message);

    for (SignalServiceDataMessage.Preview preview : previews.get()) {
      Optional<Attachment> thumbnail     = PointerAttachment.forPointer(preview.getImage());
      Optional<String>     url           = Optional.fromNullable(preview.getUrl());
      Optional<String>     title         = Optional.fromNullable(preview.getTitle());
      Optional<String>     description   = Optional.fromNullable(preview.getDescription());
      boolean              hasTitle      = !TextUtils.isEmpty(title.or(""));
      boolean              presentInBody = url.isPresent() && urlsInMessage.containsUrl(url.get());
      boolean              validDomain   = url.isPresent() && LinkPreviewUtil.isValidPreviewUrl(url.get());

      if (hasTitle && presentInBody && validDomain) {
        LinkPreview linkPreview = new LinkPreview(url.get(), title.or(""), description.or(""), preview.getDate(), thumbnail);
        linkPreviews.add(linkPreview);
      } else {
        warn(String.format("Discarding an invalid link preview. hasTitle: %b presentInBody: %b validDomain: %b", hasTitle, presentInBody, validDomain));
      }
    }

    return Optional.of(linkPreviews);
  }

  private Optional<List<Mention>> getMentions(Optional<List<SignalServiceDataMessage.Mention>> signalServiceMentions) {
    if (!signalServiceMentions.isPresent()) return Optional.absent();

    return Optional.of(getMentions(signalServiceMentions.get()));
  }

  private @NonNull List<Mention> getMentions(@Nullable List<SignalServiceDataMessage.Mention> signalServiceMentions) {
    if (signalServiceMentions == null || signalServiceMentions.isEmpty()) {
      return Collections.emptyList();
    }

    List<Mention> mentions = new ArrayList<>(signalServiceMentions.size());

    for (SignalServiceDataMessage.Mention mention : signalServiceMentions) {
      mentions.add(new Mention(Recipient.externalPush(context, mention.getAci(), null, false).getId(), mention.getStart(), mention.getLength()));
    }

    return mentions;
  }

  private Optional<InsertResult> insertPlaceholder(@NonNull String sender, int senderDevice, long timestamp) {
    return insertPlaceholder(sender, senderDevice, timestamp, Optional.absent());
  }

  private Optional<InsertResult> insertPlaceholder(@NonNull String sender, int senderDevice, long timestamp, Optional<GroupId> groupId) {
    MessageDatabase database = DatabaseFactory.getSmsDatabase(context);
    IncomingTextMessage textMessage = new IncomingTextMessage(Recipient.external(context, sender).getId(),
                                                              senderDevice, timestamp, -1, System.currentTimeMillis(), "",
                                                              groupId, 0, false, null);

    textMessage = new IncomingEncryptedMessage(textMessage, "");
    return database.insertMessageInbox(textMessage);
  }

  private Recipient getSyncMessageDestination(@NonNull SentTranscriptMessage message)
      throws BadGroupIdException
  {
    return getGroupRecipient(message.getMessage().getGroupContext()).or(() -> Recipient.externalPush(context, message.getDestination().get()));
  }

  private Recipient getMessageDestination(@NonNull SignalServiceContent content) throws BadGroupIdException {
    SignalServiceDataMessage message = content.getDataMessage().orNull();
    return getGroupRecipient(message != null ? message.getGroupContext() : Optional.absent()).or(() -> Recipient.externalHighTrustPush(context, content.getSender()));
  }

  private Optional<Recipient> getGroupRecipient(Optional<SignalServiceGroupContext> message)
      throws BadGroupIdException
  {
    if (message.isPresent()) {
      return Optional.of(Recipient.externalPossiblyMigratedGroup(context, GroupUtil.idFromGroupContext(message.get())));
    }
    return Optional.absent();
  }

  private void notifyTypingStoppedFromIncomingMessage(@NonNull Recipient senderRecipient, @NonNull Recipient conversationRecipient, int device) {
    long threadId = DatabaseFactory.getThreadDatabase(context).getOrCreateThreadIdFor(conversationRecipient);

    if (threadId > 0 && TextSecurePreferences.isTypingIndicatorsEnabled(context)) {
      Log.d(TAG, "Typing stopped on thread " + threadId + " due to an incoming message.");
      ApplicationDependencies.getTypingStatusRepository().onTypingStopped(context, threadId, senderRecipient, device, true);
    }
  }

  private boolean shouldIgnore(@NonNull SignalServiceContent content, @NonNull Recipient sender, @NonNull Recipient conversation)
      throws BadGroupIdException
  {
    if (content.getDataMessage().isPresent()) {
      SignalServiceDataMessage message = content.getDataMessage().get();

      if (conversation.isGroup() && conversation.isBlocked()) {
        return true;
      } else if (conversation.isGroup()) {
        GroupDatabase     groupDatabase = DatabaseFactory.getGroupDatabase(context);
        Optional<GroupId> groupId       = GroupUtil.idFromGroupContext(message.getGroupContext());

        if (groupId.isPresent() &&
            groupId.get().isV1() &&
            message.isGroupV1Update() &&
            groupDatabase.groupExists(groupId.get().requireV1().deriveV2MigrationGroupId()))
        {
          warn(String.valueOf(content.getTimestamp()), "Ignoring V1 update for a group we've already migrated to V2.");
          return true;
        }

        if (groupId.isPresent() && groupDatabase.isUnknownGroup(groupId.get())) {
          return sender.isBlocked();
        }

        boolean isTextMessage    = message.getBody().isPresent();
        boolean isMediaMessage   = message.getAttachments().isPresent() || message.getQuote().isPresent() || message.getSharedContacts().isPresent();
        boolean isExpireMessage  = message.isExpirationUpdate();
        boolean isGv2Update      = message.isGroupV2Update();
        boolean isContentMessage = !message.isGroupV1Update() && !isGv2Update && !isExpireMessage && (isTextMessage || isMediaMessage);
        boolean isGroupActive    = groupId.isPresent() && groupDatabase.isActive(groupId.get());
        boolean isLeaveMessage   = message.getGroupContext().isPresent() && message.getGroupContext().get().getGroupV1Type() == SignalServiceGroup.Type.QUIT;

        return (isContentMessage && !isGroupActive) || (sender.isBlocked() && !isLeaveMessage && !isGv2Update);
      } else {
        return sender.isBlocked();
      }
    } else if (content.getCallMessage().isPresent()) {
      return sender.isBlocked();
    } else if (content.getTypingMessage().isPresent()) {
      if (sender.isBlocked()) {
        return true;
      }

      if (content.getTypingMessage().get().getGroupId().isPresent()) {
        GroupId   groupId        = GroupId.push(content.getTypingMessage().get().getGroupId().get());
        Recipient groupRecipient = Recipient.externalPossiblyMigratedGroup(context, groupId);
        if (groupRecipient.isBlocked() || !groupRecipient.isActiveGroup()) {
          return true;
        } else {
          Optional<GroupRecord> groupRecord = DatabaseFactory.getGroupDatabase(context).getGroup(groupId);
          return groupRecord.isPresent() && groupRecord.get().isAnnouncementGroup() && !groupRecord.get().getAdmins().contains(sender);
        }
      }
    }

    return false;
  }

  private void resetRecipientToPush(@NonNull Recipient recipient) {
    if (recipient.isForceSmsSelection()) {
      DatabaseFactory.getRecipientDatabase(context).setForceSmsSelection(recipient.getId(), false);
    }
  }

  private void forceStickerDownloadIfNecessary(long messageId, List<DatabaseAttachment> stickerAttachments) {
    if (stickerAttachments.isEmpty()) return;

    DatabaseAttachment stickerAttachment = stickerAttachments.get(0);

    if (stickerAttachment.getTransferState() != AttachmentDatabase.TRANSFER_PROGRESS_DONE) {
      AttachmentDownloadJob downloadJob = new AttachmentDownloadJob(messageId, stickerAttachment.getAttachmentId(), true);

      try {
        downloadJob.setContext(context);
        downloadJob.doWork();
      } catch (Exception e) {
        warn("Failed to download sticker inline. Scheduling.");
        ApplicationDependencies.getJobManager().add(downloadJob);
      }
    }
  }

  private static boolean isUnidentified(@NonNull SentTranscriptMessage message, @NonNull Recipient recipient) {
    boolean unidentified = false;

    if (recipient.hasE164()) {
      unidentified |= message.isUnidentified(recipient.requireE164());
    }

    if (recipient.hasAci()) {
      unidentified |= message.isUnidentified(recipient.requireAci());
    }

    return unidentified;
  }

  private static void log(@NonNull String message) {
    Log.i(TAG, message);
  }

  private static void log(long timestamp, @NonNull String message) {
    log(String.valueOf(timestamp), message);
  }

  private static void log(@NonNull String extra, @NonNull String message) {
    String extraLog = Util.isEmpty(extra) ? "" : "[" + extra + "] ";
    Log.i(TAG, extraLog + message);
  }

  private static void warn(@NonNull String message) {
    warn("", message, null);
  }

  private static void warn(@NonNull String extra, @NonNull String message) {
    warn(extra, message, null);
  }

  private static void warn(long timestamp, @NonNull String message) {
    warn(String.valueOf(timestamp), message);
  }

  private static void warn(long timestamp, @NonNull String message, @Nullable Throwable t) {
    warn(String.valueOf(timestamp), message, t);
  }

  private static void warn(@NonNull String message, @Nullable Throwable t) {
    warn("", message, t);
  }

  private static void warn(@NonNull String extra, @NonNull String message, @Nullable Throwable t) {
    String extraLog = Util.isEmpty(extra) ? "" : "[" + extra + "] ";
    Log.w(TAG, extraLog + message, t);
  }

  private static String formatSender(@NonNull Recipient recipient, @Nullable SignalServiceContent content) {
    return formatSender(recipient.getId(), content);
  }

  private static String formatSender(@NonNull RecipientId recipientId, @Nullable SignalServiceContent content) {
    if (content != null) {
      return recipientId + " (" + content.getSender().getIdentifier() + "." + content.getSenderDevice() + ")";
    } else {
      return recipientId.toString();
    }
  }

  @SuppressWarnings("WeakerAccess")
  private static class StorageFailedException extends Exception {
    private final String sender;
    private final int    senderDevice;

    private StorageFailedException(Exception e, String sender, int senderDevice) {
      super(e);
      this.sender       = sender;
      this.senderDevice = senderDevice;
    }

    public String getSender() {
      return sender;
    }

    public int getSenderDevice() {
      return senderDevice;
    }
  }

  public enum MessageState {
    DECRYPTED_OK,
    INVALID_VERSION,
    CORRUPT_MESSAGE, // Not used, but can't remove due to serialization
    NO_SESSION,      // Not used, but can't remove due to serialization
    LEGACY_MESSAGE,
    DUPLICATE_MESSAGE,
    UNSUPPORTED_DATA_MESSAGE,
    NOOP
  }

  public static final class ExceptionMetadata {
    @NonNull private final  String  sender;
    private final           int     senderDevice;
    @Nullable private final GroupId groupId;

    public ExceptionMetadata(@NonNull String sender, int senderDevice, @Nullable GroupId groupId) {
      this.sender       = sender;
      this.senderDevice = senderDevice;
      this.groupId      = groupId;
    }

    public ExceptionMetadata(@NonNull String sender, int senderDevice) {
      this(sender, senderDevice, null);
    }

    @NonNull
    public String getSender() {
      return sender;
    }

    public int getSenderDevice() {
      return senderDevice;
    }

    @Nullable
    public GroupId getGroupId() {
      return groupId;
    }
  }
}