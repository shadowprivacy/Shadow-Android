package su.sres.securesms.components.settings.app.subscription.manage

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.subjects.PublishSubject
import org.whispersystems.libsignal.util.guava.Optional
import su.sres.securesms.components.settings.app.subscription.SubscriptionsRepository
import su.sres.securesms.keyvalue.SignalStore
import su.sres.securesms.recipients.Recipient
import su.sres.securesms.subscription.LevelUpdateOperation
import su.sres.securesms.util.livedata.Store
import su.sres.signalservice.api.subscriptions.ActiveSubscription

class ManageDonationsViewModel(
  private val subscriptionsRepository: SubscriptionsRepository
) : ViewModel() {

  private val store = Store(ManageDonationsState())
  private val eventPublisher = PublishSubject.create<ManageDonationsEvent>()
  private val disposables = CompositeDisposable()

  val state: LiveData<ManageDonationsState> = store.stateLiveData
  val events: Observable<ManageDonationsEvent> = eventPublisher.observeOn(AndroidSchedulers.mainThread())

  /* init {
    store.update(Recipient.self().live().liveDataResolved) { self, state ->
      state.copy(featuredBadge = self.featuredBadge)
    }
  } */

  override fun onCleared() {
    disposables.clear()
  }

  fun refresh() {
    disposables.clear()
    val levelUpdateOperationEdges: Observable<Optional<LevelUpdateOperation>> = SignalStore.donationsValues().levelUpdateOperationObservable.distinctUntilChanged()
    val activeSubscription: Single<ActiveSubscription> = subscriptionsRepository.getActiveSubscription()

    disposables += levelUpdateOperationEdges.flatMapSingle { optionalKey ->
      if (optionalKey.isPresent) {
        Single.just(ManageDonationsState.TransactionState.InTransaction)
      } else {
        activeSubscription.map { ManageDonationsState.TransactionState.NotInTransaction(it) }
      }
    }.subscribeBy(
      onNext = { transactionState ->
        store.update {
          it.copy(transactionState = transactionState)
        }

        if (transactionState is ManageDonationsState.TransactionState.NotInTransaction && !transactionState.activeSubscription.isActive) {
          eventPublisher.onNext(ManageDonationsEvent.NOT_SUBSCRIBED)
        }
      },
      onError = {
        eventPublisher.onNext(ManageDonationsEvent.ERROR_GETTING_SUBSCRIPTION)
      }
    )

    disposables += subscriptionsRepository.getSubscriptions(SignalStore.donationsValues().getSubscriptionCurrency()).subscribeBy { subs ->
      store.update { it.copy(availableSubscriptions = subs) }
    }
  }

  class Factory(
    private val subscriptionsRepository: SubscriptionsRepository
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
      return modelClass.cast(ManageDonationsViewModel(subscriptionsRepository))!!
    }
  }
}