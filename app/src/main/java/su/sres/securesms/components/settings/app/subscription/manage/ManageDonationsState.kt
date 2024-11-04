package su.sres.securesms.components.settings.app.subscription.manage

import su.sres.securesms.badges.models.Badge
import su.sres.securesms.subscription.Subscription
import su.sres.signalservice.api.subscriptions.ActiveSubscription

data class ManageDonationsState(
  // val featuredBadge: Badge? = null,
  val transactionState: TransactionState = TransactionState.Init,
  val availableSubscriptions: List<Subscription> = emptyList()
) {
  sealed class TransactionState {
    object Init : TransactionState()
    object InTransaction : TransactionState()
    class NotInTransaction(val activeSubscription: ActiveSubscription) : TransactionState()
  }
}