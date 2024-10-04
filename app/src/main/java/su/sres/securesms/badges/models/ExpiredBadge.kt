package su.sres.securesms.badges.models

import android.view.View
import su.sres.securesms.R
import su.sres.securesms.badges.BadgeImageView
import su.sres.securesms.components.settings.PreferenceModel
import su.sres.securesms.util.MappingAdapter
import su.sres.securesms.util.MappingViewHolder

object ExpiredBadge {

  class Model(val badge: Badge) : PreferenceModel<Model>() {
    override fun areItemsTheSame(newItem: Model): Boolean {
      return newItem.badge.id == badge.id
    }

    override fun areContentsTheSame(newItem: Model): Boolean {
      return super.areContentsTheSame(newItem) && newItem.badge == badge
    }
  }

  class ViewHolder(itemView: View) : MappingViewHolder<Model>(itemView) {

    private val badge: BadgeImageView = itemView.findViewById(R.id.expired_badge)

    override fun bind(model: Model) {
      badge.setBadge(model.badge)
    }
  }

  fun register(adapter: MappingAdapter) {
    adapter.registerFactory(Model::class.java, MappingAdapter.LayoutFactory({ ViewHolder(it) }, R.layout.expired_badge_preference))
  }
}