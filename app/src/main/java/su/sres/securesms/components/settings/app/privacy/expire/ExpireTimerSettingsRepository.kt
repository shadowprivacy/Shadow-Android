package su.sres.securesms.components.settings.app.privacy.expire

import android.content.Context
import androidx.annotation.WorkerThread
import su.sres.core.util.concurrent.SignalExecutors
import su.sres.core.util.logging.Log
import su.sres.securesms.database.ShadowDatabase
import su.sres.securesms.database.ThreadDatabase
import su.sres.securesms.groups.GroupChangeException
import su.sres.securesms.groups.GroupManager
import su.sres.securesms.mms.OutgoingExpirationUpdateMessage
import su.sres.securesms.recipients.Recipient
import su.sres.securesms.recipients.RecipientId
import su.sres.securesms.sms.MessageSender
import java.io.IOException

private val TAG: String = Log.tag(ExpireTimerSettingsRepository::class.java)

/**
 * Provide operations to set expire timer for individuals and groups.
 */
class ExpireTimerSettingsRepository(val context: Context) {

  fun setExpiration(recipientId: RecipientId, newExpirationTime: Int, consumer: (Result<Int>) -> Unit) {
    SignalExecutors.BOUNDED.execute {
      val recipient = Recipient.resolved(recipientId)
      if (recipient.groupId.isPresent && recipient.groupId.get().isPush) {
        try {
          GroupManager.updateGroupTimer(context, recipient.groupId.get().requirePush(), newExpirationTime)
          consumer.invoke(Result.success(newExpirationTime))
        } catch (e: GroupChangeException) {
          Log.w(TAG, e)
          consumer.invoke(Result.failure(e))
        } catch (e: IOException) {
          Log.w(TAG, e)
          consumer.invoke(Result.failure(e))
        }
      } else {
        ShadowDatabase.recipients.setExpireMessages(recipientId, newExpirationTime)
        val outgoingMessage = OutgoingExpirationUpdateMessage(Recipient.resolved(recipientId), System.currentTimeMillis(), newExpirationTime * 1000L)
        MessageSender.send(context, outgoingMessage, getThreadId(recipientId), false, null, null)
        consumer.invoke(Result.success(newExpirationTime))
      }
    }
  }

  @WorkerThread
  private fun getThreadId(recipientId: RecipientId): Long {
    val threadDatabase: ThreadDatabase = ShadowDatabase.threads
    val recipient: Recipient = Recipient.resolved(recipientId)
    return threadDatabase.getOrCreateThreadIdFor(recipient)
  }
}