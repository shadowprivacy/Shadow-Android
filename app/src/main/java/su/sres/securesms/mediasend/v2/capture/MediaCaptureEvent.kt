package su.sres.securesms.mediasend.v2.capture

import su.sres.securesms.mediasend.Media

sealed class MediaCaptureEvent {
  data class MediaCaptureRendered(val media: Media) : MediaCaptureEvent()
  object MediaCaptureRenderFailed : MediaCaptureEvent()
}