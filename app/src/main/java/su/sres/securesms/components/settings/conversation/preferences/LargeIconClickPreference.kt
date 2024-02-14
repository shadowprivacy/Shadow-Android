package su.sres.securesms.components.settings.conversation.preferences

import android.view.View
import su.sres.securesms.R
import su.sres.securesms.components.settings.DSLSettingsIcon
import su.sres.securesms.components.settings.DSLSettingsText
import su.sres.securesms.components.settings.PreferenceModel
import su.sres.securesms.components.settings.PreferenceViewHolder
import su.sres.securesms.util.MappingAdapter

/**
 * Renders a preference line item with a larger (40dp) icon
 */
object LargeIconClickPreference {

  fun register(adapter: MappingAdapter) {
    adapter.registerFactory(Model::class.java, MappingAdapter.LayoutFactory(::ViewHolder, R.layout.large_icon_preference_item))
  }

  class Model(
    override val title: DSLSettingsText?,
    override val icon: DSLSettingsIcon,
    val onClick: () -> Unit
  ) : PreferenceModel<Model>()

  private class ViewHolder(itemView: View) : PreferenceViewHolder<Model>(itemView) {
    override fun bind(model: Model) {
      super.bind(model)
      itemView.setOnClickListener { model.onClick() }
    }
  }
}