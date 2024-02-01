package su.sres.securesms.conversation.colors

import android.content.Context
import android.graphics.Color
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import su.sres.securesms.R
import su.sres.securesms.recipients.Recipient
import su.sres.securesms.recipients.RecipientId
import su.sres.securesms.util.Projection

/**
 * Helper class for all things ChatColors.
 *
 * - Maintains a mapping for group recipient colors
 * - Gives easy access to different bubble colors
 * - Watches and responds to RecyclerView scroll and layout changes to update a ColorizerView
 */
class Colorizer(private val colorizerView: ColorizerView) : RecyclerView.OnScrollListener(), View.OnLayoutChangeListener {

  private var colorsHaveBeenSet = false
  private val groupSenderColors: MutableMap<RecipientId, NameColor> = mutableMapOf()

  @ColorInt
  fun getOutgoingBodyTextColor(context: Context): Int {
    return ContextCompat.getColor(context, R.color.white)
  }

  @ColorInt
  fun getOutgoingFooterTextColor(context: Context): Int {
    return ContextCompat.getColor(context, R.color.conversation_item_outgoing_footer_fg)
  }

  @ColorInt
  fun getOutgoingFooterIconColor(context: Context): Int {
    return ContextCompat.getColor(context, R.color.conversation_item_outgoing_footer_fg)
  }

  @ColorInt
  fun getIncomingGroupSenderColor(context: Context, recipient: Recipient): Int = groupSenderColors[recipient.id]?.getColor(context) ?: getDefaultColor(context, recipient.id)

  fun attachToRecyclerView(recyclerView: RecyclerView) {
    recyclerView.addOnScrollListener(this)
    recyclerView.addOnLayoutChangeListener(this)
  }

  fun onNameColorsChanged(nameColorMap: Map<RecipientId, NameColor>) {
    groupSenderColors.clear()
    groupSenderColors.putAll(nameColorMap)
    colorsHaveBeenSet = true
  }

  fun onChatColorsChanged(chatColors: ChatColors) {
    colorizerView.background = chatColors.chatBubbleMask
  }

  override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
    applyClipPathsToMaskedGradient(recyclerView)
  }

  override fun onLayoutChange(v: View?, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
    applyClipPathsToMaskedGradient(v as RecyclerView)
  }

  fun applyClipPathsToMaskedGradient(recyclerView: RecyclerView) {
    val layoutManager = recyclerView.layoutManager as LinearLayoutManager

    val firstVisibleItemPosition: Int = layoutManager.findFirstVisibleItemPosition()
    val lastVisibleItemPosition: Int = layoutManager.findLastVisibleItemPosition()

    val projections: List<Projection> = (firstVisibleItemPosition..lastVisibleItemPosition)
      .mapNotNull { recyclerView.findViewHolderForAdapterPosition(it) as? Colorizable }
      .map {
        it.colorizerProjections
          .map { p -> Projection.translateFromRootToDescendantCoords(p, colorizerView) }
      }
      .flatten()

    if (projections.isNotEmpty()) {
      colorizerView.visibility = View.VISIBLE
      colorizerView.setProjections(projections)
    } else {
      colorizerView.visibility = View.GONE
    }
  }

  @ColorInt
  private fun getDefaultColor(context: Context, recipientId: RecipientId): Int {
    return if (colorsHaveBeenSet) {
      val color = ChatColorsPalette.Names.all[groupSenderColors.size % ChatColorsPalette.Names.all.size]
      groupSenderColors[recipientId] = color
      return color.getColor(context)
    } else {
      Color.TRANSPARENT
    }
  }
}