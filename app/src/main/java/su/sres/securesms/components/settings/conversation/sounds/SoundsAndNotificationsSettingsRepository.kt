package su.sres.securesms.components.settings.conversation.sounds

import android.content.Context
import su.sres.core.util.concurrent.SignalExecutors
import su.sres.securesms.database.RecipientDatabase
import su.sres.securesms.database.ShadowDatabase
import su.sres.securesms.notifications.NotificationChannels
import su.sres.securesms.recipients.Recipient
import su.sres.securesms.recipients.RecipientId

class SoundsAndNotificationsSettingsRepository(private val context: Context) {

  fun setMuteUntil(recipientId: RecipientId, muteUntil: Long) {
    SignalExecutors.BOUNDED.execute {
      ShadowDatabase.recipients.setMuted(recipientId, muteUntil)
    }
  }

  fun setMentionSetting(recipientId: RecipientId, mentionSetting: RecipientDatabase.MentionSetting) {
    SignalExecutors.BOUNDED.execute {
      ShadowDatabase.recipients.setMentionSetting(recipientId, mentionSetting)
    }
  }

  fun hasCustomNotificationSettings(recipientId: RecipientId, consumer: (Boolean) -> Unit) {
    SignalExecutors.BOUNDED.execute {
      val recipient = Recipient.resolved(recipientId)
      consumer(
        if (recipient.notificationChannel != null || !NotificationChannels.supported()) {
          true
        } else {
          NotificationChannels.updateWithShortcutBasedChannel(context, recipient)
        }
      )
    }
  }
}