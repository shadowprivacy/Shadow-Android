package su.sres.securesms.subscription

import su.sres.signalservice.api.subscriptions.SubscriberId

data class Subscriber(
  val subscriberId: SubscriberId,
  val currencyCode: String
)