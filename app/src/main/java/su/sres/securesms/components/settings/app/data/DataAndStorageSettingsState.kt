package su.sres.securesms.components.settings.app.data

import su.sres.securesms.mms.SentMediaQuality
import su.sres.securesms.webrtc.CallBandwidthMode

data class DataAndStorageSettingsState(
  val totalStorageUse: Long,
  val mobileAutoDownloadValues: Set<String>,
  val wifiAutoDownloadValues: Set<String>,
  val roamingAutoDownloadValues: Set<String>,
  val callBandwidthMode: CallBandwidthMode,
  val isProxyEnabled: Boolean,
  val sentMediaQuality: SentMediaQuality,
  val updateInRoaming: Boolean,
)