package su.sres.securesms.components.settings.app.subscription

import io.reactivex.rxjava3.core.Single
import su.sres.core.util.money.FiatMoney
import su.sres.securesms.badges.Badges
import su.sres.securesms.keyvalue.SignalStore
import su.sres.securesms.subscription.Subscription
import su.sres.signalservice.api.services.DonationsService
import su.sres.signalservice.api.subscriptions.ActiveSubscription
import java.util.Currency

/**
 * Repository which can query for the user's active subscription as well as a list of available subscriptions,
 * in the currency indicated.
 */
class SubscriptionsRepository(private val donationsService: DonationsService) {

  fun getActiveSubscription(): Single<ActiveSubscription> {
    val localSubscription = SignalStore.donationsValues().getSubscriber()
    return if (localSubscription != null) {
      donationsService.getSubscription(localSubscription.subscriberId).flatMap {
        when {
          it.status == 200 -> Single.just(it.result.get())
          it.applicationError.isPresent -> Single.error(it.applicationError.get())
          it.executionError.isPresent -> Single.error(it.executionError.get())
          else -> throw AssertionError()
        }
      }
    } else {
      Single.just(ActiveSubscription(null))
    }
  }

  fun getSubscriptions(currency: Currency): Single<List<Subscription>> = donationsService.subscriptionLevels.map { response ->
    response.result.transform { subscriptionLevels ->
      subscriptionLevels.levels.map { (code, level) ->
        Subscription(
          id = code,
          title = level.badge.name,
          badge = Badges.fromServiceBadge(level.badge),
          price = FiatMoney(level.currencies[currency.currencyCode]!!, currency),
          level = code.toInt()
        )
      }.sortedBy {
        it.level
      }
    }.or(emptyList())
  }
}