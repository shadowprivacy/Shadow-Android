package su.sres.securesms.components.settings.conversation.sounds

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import su.sres.securesms.database.RecipientDatabase
import su.sres.securesms.notifications.NotificationChannels
import su.sres.securesms.recipients.Recipient
import su.sres.securesms.recipients.RecipientId
import su.sres.securesms.util.livedata.Store

class SoundsAndNotificationsSettingsViewModel(
  private val recipientId: RecipientId,
  private val repository: SoundsAndNotificationsSettingsRepository
) : ViewModel() {

  private val store = Store(SoundsAndNotificationsSettingsState())

  val state: LiveData<SoundsAndNotificationsSettingsState> = store.stateLiveData

  init {
    store.update(Recipient.live(recipientId).liveData) { recipient, state ->
      state.copy(
        recipientId = recipientId,
        muteUntil = if (recipient.isMuted) recipient.muteUntil else 0L,
        mentionSetting = recipient.mentionSetting,
        hasMentionsSupport = recipient.isPushV2Group,
        hasCustomNotificationSettings = recipient.notificationChannel != null || !NotificationChannels.supported()
      )
    }
  }

  fun setMuteUntil(muteUntil: Long) {
    repository.setMuteUntil(recipientId, muteUntil)
  }

  fun unmute() {
    repository.setMuteUntil(recipientId, 0L)
  }

  fun setMentionSetting(mentionSetting: RecipientDatabase.MentionSetting) {
    repository.setMentionSetting(recipientId, mentionSetting)
  }

  class Factory(
    private val recipientId: RecipientId,
    private val repository: SoundsAndNotificationsSettingsRepository
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
      return requireNotNull(modelClass.cast(SoundsAndNotificationsSettingsViewModel(recipientId, repository)))
    }
  }
}