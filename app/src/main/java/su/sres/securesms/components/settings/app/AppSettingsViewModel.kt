package su.sres.securesms.components.settings.app

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.kotlin.subscribeBy
import su.sres.core.util.logging.Log
import su.sres.securesms.components.settings.app.subscription.SubscriptionsRepository
import su.sres.securesms.conversationlist.model.UnreadPaymentsLiveData
import su.sres.securesms.keyvalue.SignalStore
import su.sres.securesms.recipients.Recipient
import su.sres.securesms.util.FeatureFlags
import su.sres.securesms.util.livedata.Store
import su.sres.signalservice.api.push.exceptions.NotFoundException
import su.sres.signalservice.api.push.exceptions.PushNetworkException
import java.util.concurrent.TimeUnit

class AppSettingsViewModel(private val subscriptionsRepository: SubscriptionsRepository) : ViewModel() {

  private val store = Store(AppSettingsState(Recipient.self(), 0, false))

  private val unreadPaymentsLiveData = UnreadPaymentsLiveData()
  private val selfLiveData: LiveData<Recipient> = Recipient.self().live().liveData

  val state: LiveData<AppSettingsState> = store.stateLiveData

  init {
    store.update(unreadPaymentsLiveData) { payments, state -> state.copy(unreadPaymentsCount = payments.transform { it.unreadCount }.or(0)) }
    store.update(selfLiveData) { self, state -> state.copy(self = self) }
  }

  fun refreshActiveSubscription() {
    if (!FeatureFlags.donorBadges()) {
      return
    }

    store.update {
      it.copy(hasActiveSubscription = TimeUnit.SECONDS.toMillis(SignalStore.donationsValues().getLastEndOfPeriod()) > System.currentTimeMillis())
    }

    subscriptionsRepository.getActiveSubscription().subscribeBy(
      onSuccess = { subscription -> store.update { it.copy(hasActiveSubscription = subscription.activeSubscription != null) } },
      onError = { throwable ->
        if (throwable.isNotFoundException()) {
          Log.w(TAG, "Could not load active subscription due to unset SubscriberId (404).")
        }
        Log.w(TAG, "Could not load active subscription", throwable)
      }
    )
  }

  class Factory(private val subscriptionsRepository: SubscriptionsRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
      return modelClass.cast(AppSettingsViewModel(subscriptionsRepository)) as T
    }
  }

  companion object {
    private val TAG = Log.tag(AppSettingsViewModel::class.java)
  }

  private fun Throwable.isNotFoundException(): Boolean {
    return this is PushNetworkException && this.cause is NotFoundException || this is NotFoundException
  }
}