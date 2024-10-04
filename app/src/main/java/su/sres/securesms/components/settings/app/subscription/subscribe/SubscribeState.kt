package su.sres.securesms.components.settings.app.subscription.subscribe

import su.sres.securesms.badges.models.Badge
import su.sres.securesms.components.settings.app.subscription.models.CurrencySelection
import su.sres.securesms.subscription.Subscription

data class SubscribeState(
  val previewBadge: Badge? = null,
  val currencySelection: CurrencySelection = CurrencySelection("USD"),
  val subscriptions: List<Subscription> = listOf(),
  val selectedSubscription: Subscription? = null,
  val activeSubscription: Subscription? = null,
  val isGooglePayAvailable: Boolean = false,
  val stage: Stage = Stage.INIT
) {
  enum class Stage {
    INIT,
    READY,
    PAYMENT_PIPELINE,
    CANCELLING
  }
}