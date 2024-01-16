package su.sres.securesms.components.settings.app

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import su.sres.securesms.conversationlist.model.UnreadPaymentsLiveData
import su.sres.securesms.recipients.Recipient
import su.sres.securesms.util.livedata.LiveDataUtil

class AppSettingsViewModel : ViewModel() {

  val unreadPaymentsLiveData = UnreadPaymentsLiveData()
  val selfLiveData: LiveData<Recipient> = Recipient.self().live().liveData

  val state: LiveData<AppSettingsState> = LiveDataUtil.combineLatest(unreadPaymentsLiveData, selfLiveData) { payments, self ->
    val unreadPaymentsCount = payments.transform { it.unreadCount }.or(0)

    AppSettingsState(self, unreadPaymentsCount)
  }
}