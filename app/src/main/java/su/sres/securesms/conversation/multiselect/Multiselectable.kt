package su.sres.securesms.conversation.multiselect

import android.view.View
import su.sres.securesms.conversation.ConversationMessage
import su.sres.securesms.conversation.colors.Colorizable
import su.sres.securesms.giph.mp4.GiphyMp4Playable

interface Multiselectable : Colorizable, GiphyMp4Playable {
  val conversationMessage: ConversationMessage

  fun getTopBoundaryOfMultiselectPart(multiselectPart: MultiselectPart): Int

  fun getBottomBoundaryOfMultiselectPart(multiselectPart: MultiselectPart): Int

  fun getMultiselectPartForLatestTouch(): MultiselectPart

  fun getHorizontalTranslationTarget(): View?

  fun hasNonSelectableMedia(): Boolean
}