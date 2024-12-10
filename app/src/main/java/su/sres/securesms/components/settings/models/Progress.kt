package su.sres.securesms.components.settings.models

import android.view.View
import android.widget.TextView
import su.sres.securesms.R
import su.sres.securesms.components.settings.DSLSettingsText
import su.sres.securesms.components.settings.PreferenceModel
import su.sres.securesms.util.MappingAdapter
import su.sres.securesms.util.MappingViewHolder
import su.sres.securesms.util.visible

object Progress {

  fun register(mappingAdapter: MappingAdapter) {
    mappingAdapter.registerFactory(Model::class.java, MappingAdapter.LayoutFactory(::ViewHolder, R.layout.dsl_progress_pref))
  }

  data class Model(
    override val title: DSLSettingsText?
  ) : PreferenceModel<Model>()

  private class ViewHolder(itemView: View) : MappingViewHolder<Model>(itemView) {

    private val title: TextView = itemView.findViewById(R.id.dsl_progress_pref_title)

    override fun bind(model: Model) {
      title.text = model.title?.resolve(context)
      title.visible = model.title != null
    }
  }
}