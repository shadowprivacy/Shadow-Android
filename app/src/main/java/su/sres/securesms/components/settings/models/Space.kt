package su.sres.securesms.components.settings.models

import android.view.View
import androidx.annotation.Px
import androidx.core.view.updateLayoutParams
import su.sres.securesms.R
import su.sres.securesms.components.settings.PreferenceModel
import su.sres.securesms.util.MappingAdapter
import su.sres.securesms.util.MappingViewHolder

/**
 * Adds extra space between elements in a DSL fragment
 */
data class Space(
  @Px val pixels: Int
) {

  companion object {
    fun register(mappingAdapter: MappingAdapter) {
      mappingAdapter.registerFactory(Model::class.java, MappingAdapter.LayoutFactory({ ViewHolder(it) }, R.layout.dsl_space_preference))
    }
  }

  class Model(val space: Space) : PreferenceModel<Model>() {
    override fun areItemsTheSame(newItem: Model): Boolean {
      return true
    }

    override fun areContentsTheSame(newItem: Model): Boolean {
      return super.areContentsTheSame(newItem) && newItem.space == space
    }
  }

  class ViewHolder(itemView: View) : MappingViewHolder<Model>(itemView) {
    override fun bind(model: Model) {
      itemView.updateLayoutParams {
        height = model.space.pixels
      }
    }
  }
}