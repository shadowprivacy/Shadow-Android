package su.sres.securesms.badges.self.overview

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
import su.sres.core.util.logging.Log
import su.sres.securesms.badges.BadgeRepository
import su.sres.securesms.components.settings.app.subscription.SubscriptionsRepository
import su.sres.securesms.keyvalue.SignalStore
import su.sres.securesms.recipients.Recipient
import su.sres.securesms.util.InternetConnectionObserver
import su.sres.securesms.util.livedata.Store

private val TAG = Log.tag(BadgesOverviewViewModel::class.java)

class BadgesOverviewViewModel(
  private val badgeRepository: BadgeRepository,
  private val subscriptionsRepository: SubscriptionsRepository
) : ViewModel() {
  private val store = Store(BadgesOverviewState())
  private val eventSubject = PublishSubject.create<BadgesOverviewEvent>()

  val state: LiveData<BadgesOverviewState> = store.stateLiveData
  val events: Observable<BadgesOverviewEvent> = eventSubject.observeOn(AndroidSchedulers.mainThread())

  val disposables = CompositeDisposable()

  init {
    store.update(Recipient.live(Recipient.self().id).liveDataResolved) { recipient, state ->
      state.copy(
        stage = if (state.stage == BadgesOverviewState.Stage.INIT) BadgesOverviewState.Stage.READY else state.stage,
        allUnlockedBadges = recipient.badges,
        displayBadgesOnProfile = SignalStore.donationsValues().getDisplayBadgesOnProfile(),
        featuredBadge = recipient.featuredBadge
      )
    }

    disposables += InternetConnectionObserver.observe()
      .distinctUntilChanged()
      .subscribeBy { isConnected ->
        store.update { it.copy(hasInternet = isConnected) }
      }

    disposables += Single.zip(
      subscriptionsRepository.getActiveSubscription(),
      subscriptionsRepository.getSubscriptions()
    ) { active, all ->
      if (!active.isActive && active.activeSubscription?.willCancelAtPeriodEnd() == true) {
        Optional.fromNullable<String>(all.firstOrNull { it.level == active.activeSubscription?.level }?.badge?.id)
      } else {
        Optional.absent()
      }
    }.subscribeBy(
      onSuccess = { badgeId ->
        store.update { it.copy(fadedBadgeId = badgeId.orNull()) }
      },
      onError = { throwable ->
        Log.w(TAG, "Could not retrieve data from server", throwable)
      }
    )
  }

  fun setDisplayBadgesOnProfile(displayBadgesOnProfile: Boolean) {
    store.update { it.copy(stage = BadgesOverviewState.Stage.UPDATING_BADGE_DISPLAY_STATE) }
    disposables += badgeRepository.setVisibilityForAllBadges(displayBadgesOnProfile)
      .subscribe(
        {
          store.update { it.copy(stage = BadgesOverviewState.Stage.READY) }
        },
        { error ->
          Log.e(TAG, "Failed to update visibility.", error)
          store.update { it.copy(stage = BadgesOverviewState.Stage.READY) }
          eventSubject.onNext(BadgesOverviewEvent.FAILED_TO_UPDATE_PROFILE)
        }
      )
  }

  override fun onCleared() {
    disposables.clear()
  }

  class Factory(
    private val badgeRepository: BadgeRepository,
    private val subscriptionsRepository: SubscriptionsRepository
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
      return requireNotNull(modelClass.cast(BadgesOverviewViewModel(badgeRepository, subscriptionsRepository)))
    }
  }

  companion object {
    private val TAG = Log.tag(BadgesOverviewViewModel::class.java)
  }
}