package su.sres.securesms.components.settings.app.subscription.manage

import android.view.View
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import su.sres.securesms.R
import su.sres.securesms.badges.BadgeImageView
import su.sres.securesms.components.settings.PreferenceModel
import su.sres.securesms.payments.FiatMoneyUtil
import su.sres.securesms.subscription.Subscription
import su.sres.securesms.util.DateUtils
import su.sres.securesms.util.MappingAdapter
import su.sres.securesms.util.MappingViewHolder
import java.util.Locale

/**
 * DSL renderable item that displays active subscription information on the user's
 * manage donations page.
 */
object ActiveSubscriptionPreference {

  class Model(
    val subscription: Subscription,
    val onAddBoostClick: () -> Unit
  ) : PreferenceModel<Model>() {
    override fun areItemsTheSame(newItem: Model): Boolean {
      return subscription.id == newItem.subscription.id
    }

    override fun areContentsTheSame(newItem: Model): Boolean {
      return super.areContentsTheSame(newItem) && subscription == newItem.subscription
    }
  }

  class ViewHolder(itemView: View) : MappingViewHolder<Model>(itemView) {

    val badge: BadgeImageView = itemView.findViewById(R.id.my_support_badge)
    val title: TextView = itemView.findViewById(R.id.my_support_title)
    val price: TextView = itemView.findViewById(R.id.my_support_price)
    val expiry: TextView = itemView.findViewById(R.id.my_support_expiry)
    val boost: MaterialButton = itemView.findViewById(R.id.my_support_boost)

    override fun bind(model: Model) {
      badge.setBadge(model.subscription.badge)
      title.text = model.subscription.title

      price.text = context.getString(
        R.string.MySupportPreference__s_per_month,
        FiatMoneyUtil.format(
          context.resources,
          model.subscription.price,
          FiatMoneyUtil.formatOptions()
        )
      )

      expiry.text = context.getString(
        R.string.MySupportPreference__renews_s,
        DateUtils.formatDate(
          Locale.getDefault(),
          model.subscription.renewalTimestamp
        )
      )

      boost.setOnClickListener {
        model.onAddBoostClick()
      }
    }
  }

  fun register(adapter: MappingAdapter) {
    adapter.registerFactory(Model::class.java, MappingAdapter.LayoutFactory({ ViewHolder(it) }, R.layout.my_support_preference))
  }
}