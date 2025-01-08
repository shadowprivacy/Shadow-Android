package su.sres.securesms.components.settings.conversation.sounds.custom

import android.content.Context
import android.net.Uri
import androidx.annotation.WorkerThread
import su.sres.core.util.concurrent.SignalExecutors
import su.sres.securesms.database.RecipientDatabase
import su.sres.securesms.database.ShadowDatabase
import su.sres.securesms.keyvalue.SignalStore
import su.sres.securesms.notifications.NotificationChannels
import su.sres.securesms.recipients.Recipient
import su.sres.securesms.recipients.RecipientId
import su.sres.securesms.util.concurrent.SerialExecutor

class CustomNotificationsSettingsRepository(context: Context) {

  private val context = context.applicationContext
  private val executor = SerialExecutor(SignalExecutors.BOUNDED)

  fun initialize(recipientId: RecipientId, onInitializationComplete: () -> Unit) {
    executor.execute {
      val recipient = Recipient.resolved(recipientId)
      val database = ShadowDatabase.recipients

      if (NotificationChannels.supported() && recipient.notificationChannel != null) {
        database.setMessageRingtone(recipient.id, NotificationChannels.getMessageRingtone(context, recipient))
        database.setMessageVibrate(recipient.id, RecipientDatabase.VibrateState.fromBoolean(NotificationChannels.getMessageVibrate(context, recipient)))

        NotificationChannels.ensureCustomChannelConsistency(context)
      }

      onInitializationComplete()
    }
  }

  fun setHasCustomNotifications(recipientId: RecipientId, hasCustomNotifications: Boolean) {
    executor.execute {
      if (hasCustomNotifications) {
        createCustomNotificationChannel(recipientId)
      } else {
        deleteCustomNotificationChannel(recipientId)
      }
    }
  }

  fun setMessageVibrate(recipientId: RecipientId, vibrateState: RecipientDatabase.VibrateState) {
    executor.execute {
      val recipient: Recipient = Recipient.resolved(recipientId)

      ShadowDatabase.recipients.setMessageVibrate(recipient.id, vibrateState)
      NotificationChannels.updateMessageVibrate(context, recipient, vibrateState)
    }
  }

  fun setCallingVibrate(recipientId: RecipientId, vibrateState: RecipientDatabase.VibrateState) {
    executor.execute {
      ShadowDatabase.recipients.setCallVibrate(recipientId, vibrateState)
    }
  }

  fun setMessageSound(recipientId: RecipientId, sound: Uri?) {
    executor.execute {
      val recipient: Recipient = Recipient.resolved(recipientId)
      val defaultValue = SignalStore.settings().messageNotificationSound
      val newValue: Uri? = if (defaultValue == sound) null else sound ?: Uri.EMPTY

      ShadowDatabase.recipients.setMessageRingtone(recipient.id, newValue)
      NotificationChannels.updateMessageRingtone(context, recipient, newValue)
    }
  }

  fun setCallSound(recipientId: RecipientId, sound: Uri?) {
    executor.execute {
      val defaultValue = SignalStore.settings().callRingtone
      val newValue: Uri? = if (defaultValue == sound) null else sound ?: Uri.EMPTY

      ShadowDatabase.recipients.setCallRingtone(recipientId, newValue)
    }
  }

  @WorkerThread
  private fun createCustomNotificationChannel(recipientId: RecipientId) {
    val recipient: Recipient = Recipient.resolved(recipientId)
    val channelId = NotificationChannels.createChannelFor(context, recipient)
    ShadowDatabase.recipients.setNotificationChannel(recipient.id, channelId)
  }

  @WorkerThread
  private fun deleteCustomNotificationChannel(recipientId: RecipientId) {
    val recipient: Recipient = Recipient.resolved(recipientId)
    ShadowDatabase.recipients.setNotificationChannel(recipient.id, null)
    NotificationChannels.deleteChannelFor(context, recipient)
  }
}