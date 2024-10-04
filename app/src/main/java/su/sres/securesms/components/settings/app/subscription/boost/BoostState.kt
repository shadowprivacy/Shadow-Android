package su.sres.securesms.components.settings.app.subscription.boost

import su.sres.core.util.money.FiatMoney
import su.sres.securesms.badges.models.Badge
import su.sres.securesms.components.settings.app.subscription.models.CurrencySelection
import java.math.BigDecimal
import java.util.Currency

data class BoostState(
  val boostBadge: Badge? = null,
  val currencySelection: CurrencySelection = CurrencySelection("USD"),
  val isGooglePayAvailable: Boolean = false,
  val boosts: List<Boost> = listOf(),
  val selectedBoost: Boost? = null,
  val customAmount: FiatMoney = FiatMoney(BigDecimal.ZERO, Currency.getInstance(currencySelection.selectedCurrencyCode)),
  val isCustomAmountFocused: Boolean = false,
  val stage: Stage = Stage.INIT
) {
  enum class Stage {
    INIT,
    READY,
    PAYMENT_PIPELINE,
  }
}