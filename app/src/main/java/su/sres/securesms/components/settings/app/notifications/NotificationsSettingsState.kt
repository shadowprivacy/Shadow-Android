package su.sres.securesms.components.settings.app.notifications

import android.net.Uri

data class NotificationsSettingsState(
  val messageNotificationsState: MessageNotificationsState,
  val callNotificationsState: CallNotificationsState,
)

data class MessageNotificationsState(
  val notificationsEnabled: Boolean,
  val sound: Uri,
  val vibrateEnabled: Boolean,
  val ledColor: String,
  val ledBlink: String,
  val inChatSoundsEnabled: Boolean,
  val repeatAlerts: Int,
  val messagePrivacy: String,
  val priority: Int
)

data class CallNotificationsState(
  val notificationsEnabled: Boolean,
  val ringtone: Uri,
  val vibrateEnabled: Boolean
)