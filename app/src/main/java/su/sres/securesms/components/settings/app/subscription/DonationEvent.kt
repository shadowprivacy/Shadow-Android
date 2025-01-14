package su.sres.securesms.components.settings.app.subscription

import su.sres.securesms.badges.models.Badge

/**
 * Events that can arise from use of the donations apis.
 */
sealed class DonationEvent {
  class GooglePayUnavailableError(val throwable: Throwable) : DonationEvent()
  object RequestTokenSuccess : DonationEvent()
  class RequestTokenError(val throwable: Throwable) : DonationEvent()
  class PaymentConfirmationError(val throwable: Throwable) : DonationEvent()
  class PaymentConfirmationSuccess(val badge: Badge) : DonationEvent()
  class SubscriptionCancellationFailed(val throwable: Throwable) : DonationEvent()
  object SubscriptionCancelled : DonationEvent()
}