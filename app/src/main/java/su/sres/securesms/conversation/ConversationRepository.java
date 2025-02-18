package su.sres.securesms.conversation;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.whispersystems.libsignal.util.guava.Optional;

import su.sres.securesms.database.GroupDatabase;
import su.sres.securesms.database.ShadowDatabase;
import su.sres.securesms.database.ThreadDatabase;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientUtil;
import su.sres.securesms.util.BubbleUtil;
import su.sres.securesms.util.ConversationUtil;
import su.sres.core.util.concurrent.SignalExecutors;

import java.util.List;
import java.util.concurrent.Executor;

class ConversationRepository {

  private final Context  context;
  private final Executor executor;

  ConversationRepository() {
    this.context  = ApplicationDependencies.getApplication();
    this.executor = SignalExecutors.BOUNDED;
  }

  LiveData<ConversationData> getConversationData(long threadId, @NonNull Recipient recipient, int jumpToPosition) {
    MutableLiveData<ConversationData> liveData = new MutableLiveData<>();

    executor.execute(() -> {
      liveData.postValue(getConversationDataInternal(threadId, recipient, jumpToPosition));
    });

    return liveData;
  }

  @WorkerThread
  boolean canShowAsBubble(long threadId) {
    if (Build.VERSION.SDK_INT >= ConversationUtil.CONVERSATION_SUPPORT_VERSION) {
      Recipient recipient = ShadowDatabase.threads().getRecipientForThreadId(threadId);

      return recipient != null && BubbleUtil.canBubble(context, recipient.getId(), threadId);
    } else {
      return false;
    }
  }

  private @NonNull ConversationData getConversationDataInternal(long threadId, @NonNull Recipient conversationRecipient, int jumpToPosition) {
    ThreadDatabase.ConversationMetadata metadata                       = ShadowDatabase.threads().getConversationMetadata(threadId);
    int                                 threadSize                     = ShadowDatabase.mmsSms().getConversationCount(threadId);
    long                                lastSeen                       = metadata.getLastSeen();
    boolean                             hasSent                        = metadata.hasSent();
    int                                 lastSeenPosition               = 0;
    long                                lastScrolled                   = metadata.getLastScrolled();
    int                                 lastScrolledPosition           = 0;
    boolean                             isMessageRequestAccepted       = RecipientUtil.isMessageRequestAccepted(context, threadId);
    ConversationData.MessageRequestData messageRequestData             = new ConversationData.MessageRequestData(isMessageRequestAccepted);
    boolean                             showUniversalExpireTimerUpdate = false;

    if (lastSeen > 0) {
      lastSeenPosition = ShadowDatabase.mmsSms().getMessagePositionOnOrAfterTimestamp(threadId, lastSeen);
    }

    if (lastSeenPosition <= 0) {
      lastSeen = 0;
    }

    if (lastSeen == 0 && lastScrolled > 0) {
      lastScrolledPosition = ShadowDatabase.mmsSms().getMessagePositionOnOrAfterTimestamp(threadId, lastScrolled);
    }

    if (!isMessageRequestAccepted) {
      boolean isGroup                             = false;
      boolean recipientIsKnownOrHasGroupsInCommon = false;
      if (conversationRecipient.isGroup()) {
        Optional<GroupDatabase.GroupRecord> group = ShadowDatabase.groups().getGroup(conversationRecipient.getId());
        if (group.isPresent()) {
          List<Recipient> recipients = Recipient.resolvedList(group.get().getMembers());
          for (Recipient recipient : recipients) {
            if ((recipient.isProfileSharing() || recipient.hasGroupsInCommon()) && !recipient.isSelf()) {
              recipientIsKnownOrHasGroupsInCommon = true;
              break;
            }
          }
        }
        isGroup = true;
      } else if (conversationRecipient.hasGroupsInCommon()) {
        recipientIsKnownOrHasGroupsInCommon = true;
      }
      messageRequestData = new ConversationData.MessageRequestData(isMessageRequestAccepted, recipientIsKnownOrHasGroupsInCommon, isGroup);
    }

    if (SignalStore.settings().getUniversalExpireTimer() != 0 &&
        conversationRecipient.getExpiresInSeconds() == 0 &&
        !conversationRecipient.isGroup() &&
        conversationRecipient.isRegistered() &&
        (threadId == -1 || !ShadowDatabase.mmsSms().hasMeaningfulMessage(threadId)))
    {
      showUniversalExpireTimerUpdate = true;
    }

    return new ConversationData(threadId, lastSeen, lastSeenPosition, lastScrolledPosition, hasSent, jumpToPosition, threadSize, messageRequestData, showUniversalExpireTimerUpdate);
  }
}