package su.sres.securesms.components.settings.app.subscription.boost

import io.reactivex.rxjava3.core.Single
import su.sres.core.util.money.FiatMoney
import su.sres.securesms.badges.Badges
import su.sres.securesms.badges.models.Badge
import su.sres.securesms.util.PlatformCurrencyUtil
import su.sres.signalservice.api.profiles.SignalServiceProfile
import su.sres.signalservice.api.services.DonationsService
import su.sres.signalservice.internal.ServiceResponse
import java.math.BigDecimal
import java.util.Currency
import java.util.Locale

class BoostRepository(private val donationsService: DonationsService) {

  fun getBoosts(): Single<Map<Currency, List<Boost>>> {
    return donationsService.boostAmounts
      .flatMap(ServiceResponse<Map<String, List<BigDecimal>>>::flattenResult)
      .map { result ->
        result
          .filter { PlatformCurrencyUtil.getAvailableCurrencyCodes().contains(it.key) }
          .mapKeys { (code, _) -> Currency.getInstance(code) }
          .mapValues { (currency, prices) -> prices.map { Boost(FiatMoney(it, currency)) } }
      }
  }

  fun getBoostBadge(): Single<Badge> {
    return donationsService.getBoostBadge(Locale.getDefault())
      .flatMap(ServiceResponse<SignalServiceProfile.Badge>::flattenResult)
      .map(Badges::fromServiceBadge)
  }
}