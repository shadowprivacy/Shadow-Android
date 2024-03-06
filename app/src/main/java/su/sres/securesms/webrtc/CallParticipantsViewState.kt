package su.sres.securesms.webrtc

import su.sres.securesms.components.webrtc.CallParticipantsState

data class CallParticipantsViewState(
  val callParticipantsState: CallParticipantsState,
  val isPortrait: Boolean,
  val isLandscapeEnabled: Boolean
)