package su.sres.securesms.components.settings.app.privacy

import su.sres.securesms.keyvalue.UserLoginPrivacyValues

data class PrivacySettingsState(
  val blockedCount: Int,
  val seeMyUserLogin: UserLoginPrivacyValues.UserLoginSharingMode,
  val findMeByUserLogin: UserLoginPrivacyValues.UserLoginListingMode,
  val readReceipts: Boolean,
  val typingIndicators: Boolean,
  val screenLock: Boolean,
  val screenLockActivityTimeout: Long,
  val screenSecurity: Boolean,
  val incognitoKeyboard: Boolean,
  val isObsoletePasswordEnabled: Boolean,
  val isObsoletePasswordTimeoutEnabled: Boolean,
  val obsoletePasswordTimeout: Int,
  val universalExpireTimer: Int
)