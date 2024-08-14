package su.sres.securesms.service.webrtc.state

import su.sres.securesms.components.sensors.Orientation
import su.sres.securesms.ringrtc.CameraState
import su.sres.securesms.webrtc.audio.SignalAudioManager

/**
 * Local device specific state.
 */
data class LocalDeviceState constructor(
  var cameraState: CameraState = CameraState.UNKNOWN,
  var isMicrophoneEnabled: Boolean = true,
  var orientation: Orientation = Orientation.PORTRAIT_BOTTOM_EDGE,
  var isLandscapeEnabled: Boolean = false,
  var deviceOrientation: Orientation = Orientation.PORTRAIT_BOTTOM_EDGE,
  var activeDevice: SignalAudioManager.AudioDevice = SignalAudioManager.AudioDevice.NONE,
  var availableDevices: Set<SignalAudioManager.AudioDevice> = emptySet()
) {

  fun duplicate(): LocalDeviceState {
    return copy()
  }
}