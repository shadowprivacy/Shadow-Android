package su.sres.securesms.notifications.v2

import android.content.Context
import androidx.annotation.WorkerThread
import su.sres.securesms.database.DatabaseFactory
import su.sres.securesms.database.MmsSmsColumns
import su.sres.securesms.database.MmsSmsDatabase
import su.sres.securesms.database.RecipientDatabase
import su.sres.securesms.database.model.MessageRecord
import su.sres.securesms.database.model.ReactionRecord
import su.sres.securesms.recipients.Recipient
import su.sres.securesms.util.CursorUtil

/**
 * Queries the message databases to determine messages that should be in notifications.
 */
object NotificationStateProvider {

  @WorkerThread
  fun constructNotificationState(context: Context, stickyThreads: Map<Long, MessageNotifierV2.StickyThread>): NotificationStateV2 {
    val messages: MutableList<NotificationMessage> = mutableListOf()

    DatabaseFactory.getMmsSmsDatabase(context).getMessagesForNotificationState(stickyThreads.values).use { unreadMessages ->
      if (unreadMessages.count == 0) {
        return NotificationStateV2.EMPTY
      }

      MmsSmsDatabase.readerFor(unreadMessages).use { reader ->
        var record: MessageRecord? = reader.next
        while (record != null) {
          val threadRecipient: Recipient? = DatabaseFactory.getThreadDatabase(context).getRecipientForThreadId(record.threadId)
          if (threadRecipient != null) {
            messages += NotificationMessage(
              messageRecord = record,
              threadRecipient = threadRecipient,
              threadId = record.threadId,
              stickyThread = stickyThreads.containsKey(record.threadId),
              isUnreadMessage = CursorUtil.requireInt(unreadMessages, MmsSmsColumns.READ) == 0,
              hasUnreadReactions = CursorUtil.requireInt(unreadMessages, MmsSmsColumns.REACTIONS_UNREAD) == 1,
              lastReactionRead = CursorUtil.requireLong(unreadMessages, MmsSmsColumns.REACTIONS_LAST_SEEN)
            )
          }
          record = reader.next
        }
      }
    }

    val conversations: MutableList<NotificationConversation> = mutableListOf()
    messages.groupBy { it.threadId }
      .forEach { (threadId, threadMessages) ->
        var notificationItems: MutableList<NotificationItemV2> = mutableListOf()

        for (notification: NotificationMessage in threadMessages) {

          if (notification.includeMessage()) {
            notificationItems.add(MessageNotification(notification.threadRecipient, notification.messageRecord))
          }

          if (notification.hasUnreadReactions) {
            notification.messageRecord.reactions.filter { notification.includeReaction(it) }
              .forEach { notificationItems.add(ReactionNotification(notification.threadRecipient, notification.messageRecord, it)) }
          }
        }

        notificationItems.sort()
        if (notificationItems.isNotEmpty() && stickyThreads.containsKey(threadId) && !notificationItems.last().individualRecipient.isSelf) {
          val indexOfOldestNonSelfMessage: Int = notificationItems.indexOfLast { it.individualRecipient.isSelf } + 1
          notificationItems = notificationItems.slice(indexOfOldestNonSelfMessage..notificationItems.lastIndex).toMutableList()
        }

        if (notificationItems.isNotEmpty()) {
          conversations += NotificationConversation(notificationItems[0].threadRecipient, threadId, notificationItems)
        }
      }

    return NotificationStateV2(conversations)
  }

  private data class NotificationMessage(
    val messageRecord: MessageRecord,
    val threadRecipient: Recipient,
    val threadId: Long,
    val stickyThread: Boolean,
    val isUnreadMessage: Boolean,
    val hasUnreadReactions: Boolean,
    val lastReactionRead: Long
  ) {
    private val isUnreadIncoming: Boolean = isUnreadMessage && !messageRecord.isOutgoing

    fun includeMessage(): Boolean {
      return (isUnreadIncoming || stickyThread) && (threadRecipient.isNotMuted || (threadRecipient.isAlwaysNotifyMentions && messageRecord.hasSelfMention()))
    }

    fun includeReaction(reaction: ReactionRecord): Boolean {
      return reaction.author != Recipient.self().id && messageRecord.isOutgoing && reaction.dateReceived > lastReactionRead && threadRecipient.isNotMuted
    }

    private val Recipient.isNotMuted: Boolean
      get() = !isMuted

    private val Recipient.isAlwaysNotifyMentions: Boolean
      get() = mentionSetting == RecipientDatabase.MentionSetting.ALWAYS_NOTIFY
  }
}