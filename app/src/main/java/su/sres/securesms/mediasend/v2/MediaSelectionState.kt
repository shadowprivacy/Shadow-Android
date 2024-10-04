package su.sres.securesms.mediasend.v2

import android.net.Uri
import su.sres.securesms.TransportOption
import su.sres.securesms.mediasend.Media
import su.sres.securesms.mediasend.MediaSendConstants
import su.sres.securesms.mms.SentMediaQuality
import su.sres.securesms.recipients.Recipient

data class MediaSelectionState(
  val transportOption: TransportOption,
  val selectedMedia: List<Media> = listOf(),
  val focusedMedia: Media? = null,
  val recipient: Recipient? = null,
  // quality is server-side managed
  // val quality: SentMediaQuality = SignalStore.settings().sentMediaQuality,
  val quality: SentMediaQuality = SentMediaQuality.HIGH,
  val message: CharSequence? = null,
  val viewOnceToggleState: ViewOnceToggleState = ViewOnceToggleState.INFINITE,
  val isTouchEnabled: Boolean = true,
  val isSent: Boolean = false,
  val isPreUploadEnabled: Boolean = false,
  val isMeteredConnection: Boolean = false,
  val editorStateMap: Map<Uri, Any> = mapOf(),
  val cameraFirstCapture: Media? = null
) {

  val maxSelection = MediaSendConstants.MAX_PUSH

  enum class ViewOnceToggleState(val code: Int) {
    INFINITE(0),
    ONCE(1);

    fun next(): ViewOnceToggleState {
      return when (this) {
        INFINITE -> ONCE
        ONCE -> INFINITE
      }
    }

    companion object {
      fun fromCode(code: Int): ViewOnceToggleState {
        return when (code) {
          1 -> ONCE
          else -> INFINITE
        }
      }
    }
  }
}