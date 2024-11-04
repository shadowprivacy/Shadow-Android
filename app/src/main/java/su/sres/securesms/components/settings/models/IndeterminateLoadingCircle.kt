package su.sres.securesms.components.settings.models

import android.view.View
import su.sres.securesms.R
import su.sres.securesms.components.settings.PreferenceModel
import su.sres.securesms.util.MappingAdapter
import su.sres.securesms.util.MappingViewHolder

object IndeterminateLoadingCircle : PreferenceModel<IndeterminateLoadingCircle>() {
  override fun areItemsTheSame(newItem: IndeterminateLoadingCircle): Boolean = true

  private class ViewHolder(itemView: View) : MappingViewHolder<IndeterminateLoadingCircle>(itemView) {
    override fun bind(model: IndeterminateLoadingCircle) = Unit
  }

  fun register(mappingAdapter: MappingAdapter) {
    mappingAdapter.registerFactory(IndeterminateLoadingCircle::class.java, MappingAdapter.LayoutFactory({ ViewHolder(it) }, R.layout.indeterminate_loading_circle_pref))
  }
}