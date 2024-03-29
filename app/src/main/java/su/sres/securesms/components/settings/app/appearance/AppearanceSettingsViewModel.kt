package su.sres.securesms.components.settings.app.appearance

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import su.sres.securesms.jobs.EmojiSearchIndexDownloadJob
import su.sres.securesms.keyvalue.SignalStore
import su.sres.securesms.util.livedata.Store

class AppearanceSettingsViewModel : ViewModel() {
  private val store: Store<AppearanceSettingsState>

  init {
    val initialState = AppearanceSettingsState(
      SignalStore.settings().theme,
      SignalStore.settings().messageFontSize,
      SignalStore.settings().language
    )

    store = Store(initialState)
  }

  val state: LiveData<AppearanceSettingsState> = store.stateLiveData

  fun setTheme(theme: String) {
    store.update { it.copy(theme = theme) }
    SignalStore.settings().theme = theme
  }

  fun setLanguage(language: String) {
    store.update { it.copy(language = language) }
    SignalStore.settings().language = language
    EmojiSearchIndexDownloadJob.scheduleImmediately()
  }

  fun setMessageFontSize(size: Int) {
    store.update { it.copy(messageFontSize = size) }
    SignalStore.settings().messageFontSize = size
  }
}