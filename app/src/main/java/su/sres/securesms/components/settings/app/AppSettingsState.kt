package su.sres.securesms.components.settings.app

import su.sres.securesms.recipients.Recipient

data class AppSettingsState(
  val self: Recipient,
  val unreadPaymentsCount: Int,
  val hasActiveSubscription: Boolean
)