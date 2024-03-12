package su.sres.securesms.conversation.multiselect

import android.view.View
import su.sres.securesms.conversation.ConversationMessage
import su.sres.securesms.conversation.colors.Colorizable

interface Multiselectable : Colorizable {
  val conversationMessage: ConversationMessage

  fun getTopBoundaryOfMultiselectPart(multiselectPart: MultiselectPart): Int

  fun getBottomBoundaryOfMultiselectPart(multiselectPart: MultiselectPart): Int

  fun getMultiselectPartForLatestTouch(): MultiselectPart

  fun getHorizontalTranslationTarget(): View?

  fun hasNonSelectableMedia(): Boolean
}