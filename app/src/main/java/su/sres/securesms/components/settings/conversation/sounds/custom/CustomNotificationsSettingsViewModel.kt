package su.sres.securesms.components.settings.conversation.sounds.custom

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import su.sres.securesms.database.RecipientDatabase
import su.sres.securesms.keyvalue.SignalStore
import su.sres.securesms.notifications.NotificationChannels
import su.sres.securesms.recipients.Recipient
import su.sres.securesms.recipients.RecipientId
import su.sres.securesms.util.FeatureFlags
import su.sres.securesms.util.livedata.Store

class CustomNotificationsSettingsViewModel(
  private val recipientId: RecipientId,
  private val repository: CustomNotificationsSettingsRepository
) : ViewModel() {

  private val store = Store(CustomNotificationsSettingsState())

  val state: LiveData<CustomNotificationsSettingsState> = store.stateLiveData

  init {
    repository.initialize(recipientId) {
      store.update {
        it.copy(
          isInitialLoadComplete = true,
          controlsEnabled = (!NotificationChannels.supported() || it.hasCustomNotifications)
        )
      }
    }

    store.update(Recipient.live(recipientId).liveData) { recipient, state ->
      val recipientHasCustomNotifications = NotificationChannels.supported() && recipient.notificationChannel != null
      state.copy(
        hasCustomNotifications = recipientHasCustomNotifications,
        controlsEnabled = (!NotificationChannels.supported() || recipientHasCustomNotifications) && state.isInitialLoadComplete,
        messageSound = recipient.messageRingtone,
        messageVibrateState = recipient.messageVibrate,
        messageVibrateEnabled = when (recipient.messageVibrate) {
          RecipientDatabase.VibrateState.DEFAULT -> SignalStore.settings().isMessageVibrateEnabled
          RecipientDatabase.VibrateState.ENABLED -> true
          RecipientDatabase.VibrateState.DISABLED -> false
        },
        showCallingOptions = recipient.isRegistered && (!recipient.isGroup || FeatureFlags.groupCallRinging()),
        callSound = recipient.callRingtone,
        callVibrateState = recipient.callVibrate
      )
    }
  }

  fun setHasCustomNotifications(hasCustomNotifications: Boolean) {
    repository.setHasCustomNotifications(recipientId, hasCustomNotifications)
  }

  fun setMessageVibrate(messageVibrateState: RecipientDatabase.VibrateState) {
    repository.setMessageVibrate(recipientId, messageVibrateState)
  }

  fun setMessageSound(uri: Uri?) {
    repository.setMessageSound(recipientId, uri)
  }

  fun setCallVibrate(callVibrateState: RecipientDatabase.VibrateState) {
    repository.setCallingVibrate(recipientId, callVibrateState)
  }

  fun setCallSound(uri: Uri?) {
    repository.setCallSound(recipientId, uri)
  }

  class Factory(
    private val recipientId: RecipientId,
    private val repository: CustomNotificationsSettingsRepository
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
      return requireNotNull(modelClass.cast(CustomNotificationsSettingsViewModel(recipientId, repository)))
    }
  }
}