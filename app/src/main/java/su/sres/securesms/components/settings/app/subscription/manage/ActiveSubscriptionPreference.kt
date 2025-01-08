package su.sres.securesms.components.settings.app.subscription.manage

import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import su.sres.core.util.money.FiatMoney
import su.sres.securesms.R
import su.sres.securesms.badges.BadgeImageView
import su.sres.securesms.components.settings.PreferenceModel
import su.sres.securesms.keyvalue.SignalStore
import su.sres.securesms.payments.FiatMoneyUtil
import su.sres.securesms.subscription.Subscription
import su.sres.securesms.util.DateUtils
import su.sres.securesms.util.MappingAdapter
import su.sres.securesms.util.MappingViewHolder
import su.sres.securesms.util.SpanUtil
import su.sres.securesms.util.visible
import su.sres.signalservice.api.subscriptions.ActiveSubscription
import java.util.Locale

/**
 * DSL renderable item that displays active subscription information on the user's
 * manage donations page.
 */
object ActiveSubscriptionPreference {

  class Model(
    val price: FiatMoney,
    val subscription: Subscription,
    val onAddBoostClick: () -> Unit,
    val renewalTimestamp: Long = -1L,
    val redemptionState: ManageDonationsState.SubscriptionRedemptionState,
    val activeSubscription: ActiveSubscription.Subscription,
    val onContactSupport: () -> Unit
  ) : PreferenceModel<Model>() {
    override fun areItemsTheSame(newItem: Model): Boolean {
      return subscription.id == newItem.subscription.id
    }

    override fun areContentsTheSame(newItem: Model): Boolean {
      return super.areContentsTheSame(newItem) &&
        subscription == newItem.subscription &&
        renewalTimestamp == newItem.renewalTimestamp &&
        redemptionState == newItem.redemptionState &&
        FiatMoney.equals(price, newItem.price) &&
        activeSubscription == newItem.activeSubscription
    }
  }

  class ViewHolder(itemView: View) : MappingViewHolder<Model>(itemView) {

    val badge: BadgeImageView = itemView.findViewById(R.id.my_support_badge)
    val title: TextView = itemView.findViewById(R.id.my_support_title)
    val price: TextView = itemView.findViewById(R.id.my_support_price)
    val expiry: TextView = itemView.findViewById(R.id.my_support_expiry)
    val boost: MaterialButton = itemView.findViewById(R.id.my_support_boost)
    val progress: ProgressBar = itemView.findViewById(R.id.my_support_progress)

    override fun bind(model: Model) {
      badge.setBadge(model.subscription.badge)
      title.text = model.subscription.name

      price.text = context.getString(
        R.string.MySupportPreference__s_per_month,
        FiatMoneyUtil.format(
          context.resources,
          model.price,
          FiatMoneyUtil.formatOptions()
        )
      )

      expiry.movementMethod = LinkMovementMethod.getInstance()

      when (model.redemptionState) {
        ManageDonationsState.SubscriptionRedemptionState.NONE -> presentRenewalState(model)
        ManageDonationsState.SubscriptionRedemptionState.IN_PROGRESS -> presentInProgressState()
        ManageDonationsState.SubscriptionRedemptionState.FAILED -> presentFailureState(model)
      }

      boost.setOnClickListener {
        model.onAddBoostClick()
      }
    }

    private fun presentRenewalState(model: Model) {

      expiry.text = context.getString(
        R.string.MySupportPreference__renews_s,
        DateUtils.formatDateWithYear(
          Locale.getDefault(),
          model.renewalTimestamp
        )
      )

      badge.alpha = 1f
      progress.visible = false
    }

    private fun presentInProgressState() {
      expiry.text = context.getString(R.string.MySupportPreference__processing_transaction)
      badge.alpha = 0.2f
      progress.visible = true
    }

    private fun presentFailureState(model: Model) {
      if (model.activeSubscription.isFailedPayment || SignalStore.donationsValues().shouldCancelSubscriptionBeforeNextSubscribeAttempt) {
        presentPaymentFailureState(model)
      } else {
        presentRedemptionFailureState(model)
      }
    }

    private fun presentPaymentFailureState(model: Model) {
      val contactString = context.getString(R.string.MySupportPreference__please_contact_support)

      expiry.text = SpanUtil.clickSubstring(
        context.getString(R.string.DonationsErrors__error_processing_payment_s, contactString),
        contactString,
        {
          model.onContactSupport()
        },
        ContextCompat.getColor(context, R.color.signal_accent_primary)
      )
      badge.alpha = 0.2f
      progress.visible = false
    }

    private fun presentRedemptionFailureState(model: Model) {
      val contactString = context.getString(R.string.MySupportPreference__please_contact_support)

      expiry.text = SpanUtil.clickSubstring(
        context.getString(R.string.MySupportPreference__couldnt_add_badge_s, contactString),
        contactString,
        {
          model.onContactSupport()
        },
        ContextCompat.getColor(context, R.color.signal_accent_primary)
      )
      badge.alpha = 0.2f
      progress.visible = false
    }
  }

  fun register(adapter: MappingAdapter) {
    adapter.registerFactory(Model::class.java, MappingAdapter.LayoutFactory({ ViewHolder(it) }, R.layout.my_support_preference))
  }
}