package su.sres.securesms.components.settings.conversation.sounds

import su.sres.securesms.database.RecipientDatabase
import su.sres.securesms.recipients.Recipient
import su.sres.securesms.recipients.RecipientId

data class SoundsAndNotificationsSettingsState(
  val recipientId: RecipientId = Recipient.UNKNOWN.id,
  val muteUntil: Long = 0L,
  val mentionSetting: RecipientDatabase.MentionSetting = RecipientDatabase.MentionSetting.DO_NOT_NOTIFY,
  val hasCustomNotificationSettings: Boolean = false,
  val hasMentionsSupport: Boolean = false
)