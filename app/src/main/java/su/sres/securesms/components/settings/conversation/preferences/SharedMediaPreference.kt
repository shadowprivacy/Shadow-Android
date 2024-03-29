package su.sres.securesms.components.settings.conversation.preferences

import android.database.Cursor
import android.view.View
import su.sres.securesms.R
import su.sres.securesms.components.ThreadPhotoRailView
import su.sres.securesms.components.settings.PreferenceModel
import su.sres.securesms.database.MediaDatabase
import su.sres.securesms.mms.GlideApp
import su.sres.securesms.util.MappingAdapter
import su.sres.securesms.util.MappingViewHolder
import su.sres.securesms.util.ViewUtil

/**
 * Renders the shared media photo rail.
 */
object SharedMediaPreference {

  fun register(adapter: MappingAdapter) {
    adapter.registerFactory(Model::class.java, MappingAdapter.LayoutFactory(::ViewHolder, R.layout.conversation_settings_shared_media))
  }

  class Model(
    val mediaCursor: Cursor,
    val mediaIds: List<Long>,
    val onMediaRecordClick: (MediaDatabase.MediaRecord, Boolean) -> Unit
  ) : PreferenceModel<Model>() {
    override fun areItemsTheSame(newItem: Model): Boolean {
      return true
    }

    override fun areContentsTheSame(newItem: Model): Boolean {
      return super.areContentsTheSame(newItem) &&
        mediaIds == newItem.mediaIds
    }
  }

  private class ViewHolder(itemView: View) : MappingViewHolder<Model>(itemView) {

    private val rail: ThreadPhotoRailView = itemView.findViewById(R.id.rail_view)

    override fun bind(model: Model) {
      rail.setCursor(GlideApp.with(rail), model.mediaCursor)
      rail.setListener {
        model.onMediaRecordClick(it, ViewUtil.isLtr(rail))
      }
    }
  }
}