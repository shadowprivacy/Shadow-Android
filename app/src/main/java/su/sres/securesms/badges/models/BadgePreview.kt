package su.sres.securesms.badges.models

import android.view.View
import su.sres.securesms.R
import su.sres.securesms.badges.BadgeImageView
import su.sres.securesms.components.AvatarImageView
import su.sres.securesms.components.settings.PreferenceModel
import su.sres.securesms.recipients.Recipient
import su.sres.securesms.util.MappingAdapter
import su.sres.securesms.util.MappingViewHolder

object BadgePreview {

  fun register(mappingAdapter: MappingAdapter) {
    mappingAdapter.registerFactory(Model::class.java, MappingAdapter.LayoutFactory({ ViewHolder(it) }, R.layout.featured_badge_preview_preference))
    mappingAdapter.registerFactory(SubscriptionModel::class.java, MappingAdapter.LayoutFactory({ ViewHolder(it) }, R.layout.subscription_flow_badge_preview_preference))
  }

  abstract class BadgeModel<T : BadgeModel<T>> : PreferenceModel<T>() {
    abstract val badge: Badge?
  }

  data class Model(override val badge: Badge?) : BadgeModel<Model>() {
    override fun areItemsTheSame(newItem: Model): Boolean {
      return true
    }

    override fun areContentsTheSame(newItem: Model): Boolean {
      return super.areContentsTheSame(newItem) && badge == newItem.badge
    }

    override fun getChangePayload(newItem: Model): Any? {
      return Unit
    }
  }

  data class SubscriptionModel(override val badge: Badge?) : BadgeModel<SubscriptionModel>() {
    override fun areItemsTheSame(newItem: SubscriptionModel): Boolean {
      return true
    }

    override fun areContentsTheSame(newItem: SubscriptionModel): Boolean {
      return super.areContentsTheSame(newItem) && badge == newItem.badge
    }

    override fun getChangePayload(newItem: SubscriptionModel): Any? {
      return Unit
    }
  }

  class ViewHolder<T : BadgeModel<T>>(itemView: View) : MappingViewHolder<T>(itemView) {

    private val avatar: AvatarImageView = itemView.findViewById(R.id.avatar)
    private val badge: BadgeImageView = itemView.findViewById(R.id.badge)

    override fun bind(model: T) {
      if (payload.isEmpty()) {
        avatar.setRecipient(Recipient.self())
        avatar.disableQuickContact()
      }

      badge.setBadge(model.badge)
    }
  }
}