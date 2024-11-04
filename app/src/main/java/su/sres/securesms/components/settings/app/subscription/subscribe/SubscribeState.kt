package su.sres.securesms.components.settings.app.subscription.subscribe

import su.sres.securesms.components.settings.app.subscription.models.CurrencySelection
import su.sres.securesms.subscription.Subscription
import su.sres.signalservice.api.subscriptions.ActiveSubscription

data class SubscribeState(
  val currencySelection: CurrencySelection = CurrencySelection("USD"),
  val subscriptions: List<Subscription> = listOf(),
  val selectedSubscription: Subscription? = null,
  val activeSubscription: ActiveSubscription? = null,
  val isGooglePayAvailable: Boolean = false,
  val stage: Stage = Stage.INIT,
  val hasInProgressSubscriptionTransaction: Boolean = false,
) {
  enum class Stage {
    INIT,
    READY,
    TOKEN_REQUEST,
    PAYMENT_PIPELINE,
    CANCELLING
  }
}