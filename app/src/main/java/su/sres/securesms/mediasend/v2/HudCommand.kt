package su.sres.securesms.mediasend.v2

import android.view.KeyEvent

sealed class HudCommand {
  object StartDraw : HudCommand()
  object StartCropAndRotate : HudCommand()
  object SaveMedia : HudCommand()

  object ResumeEntryTransition : HudCommand()

  object OpenEmojiSearch : HudCommand()
  object CloseEmojiSearch : HudCommand()
  data class EmojiInsert(val emoji: String?) : HudCommand()
  data class EmojiKeyEvent(val keyEvent: KeyEvent?) : HudCommand()
}