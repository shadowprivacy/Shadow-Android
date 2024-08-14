package su.sres.securesms.mediaoverview

import android.graphics.Rect
import android.view.View
import androidx.annotation.Px
import androidx.recyclerview.widget.RecyclerView
import su.sres.securesms.components.recyclerview.GridDividerDecoration
import su.sres.securesms.util.ViewUtil

internal class MediaGridDividerDecoration(
  spanCount: Int,
  space: Int,
  private val adapter: MediaGalleryAllAdapter
) : GridDividerDecoration(spanCount, space) {

  override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
    val holder = parent.getChildViewHolder(view)

    val adapterPosition = holder.bindingAdapterPosition
    val section = adapter.getAdapterPositionSection(adapterPosition)
    val itemSectionOffset = adapter.getItemSectionOffset(section, adapterPosition)

    if (itemSectionOffset == -1) {
      return
    }

    val sectionItemViewType = adapter.getSectionItemViewType(section, itemSectionOffset)
    if (sectionItemViewType != MediaGalleryAllAdapter.GALLERY) {
      return
    }

    setItemOffsets(itemSectionOffset, view, outRect)
  }
}