package su.sres.securesms.components.settings.app.privacy

import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import su.sres.securesms.dependencies.ApplicationDependencies
import su.sres.securesms.jobs.RefreshAttributesJob
import su.sres.securesms.keyvalue.UserLoginPrivacyValues
import su.sres.securesms.keyvalue.SignalStore
import su.sres.securesms.storage.StorageSyncHelper
import su.sres.securesms.util.TextSecurePreferences
import su.sres.securesms.util.livedata.Store

class PrivacySettingsViewModel(
  private val sharedPreferences: SharedPreferences,
  private val repository: PrivacySettingsRepository
) : ViewModel() {

  private val store = Store(getState())

  val state: LiveData<PrivacySettingsState> = store.stateLiveData

  fun refreshBlockedCount() {
    repository.getBlockedCount { count ->
      store.update { it.copy(blockedCount = count) }
    }
  }

  fun setReadReceiptsEnabled(enabled: Boolean) {
    sharedPreferences.edit().putBoolean(TextSecurePreferences.READ_RECEIPTS_PREF, enabled).apply()
    repository.syncReadReceiptState()
    refresh()
  }

  fun setTypingIndicatorsEnabled(enabled: Boolean) {
    sharedPreferences.edit().putBoolean(TextSecurePreferences.TYPING_INDICATORS, enabled).apply()
    repository.syncTypingIndicatorsState()
    refresh()
  }

  fun setScreenLockEnabled(enabled: Boolean) {
    sharedPreferences.edit().putBoolean(TextSecurePreferences.SCREEN_LOCK, enabled).apply()
    refresh()
  }

  fun setScreenLockTimeout(seconds: Long) {
    TextSecurePreferences.setScreenLockTimeout(ApplicationDependencies.getApplication(), seconds)
    refresh()
  }

  fun setScreenSecurityEnabled(enabled: Boolean) {
    sharedPreferences.edit().putBoolean(TextSecurePreferences.SCREEN_SECURITY_PREF, enabled).apply()
    refresh()
  }

  fun setUserLoginSharingMode(userLoginSharingMode: UserLoginPrivacyValues.UserLoginSharingMode) {
    SignalStore.userLoginPrivacy().userLoginSharingMode = userLoginSharingMode
    // StorageSyncHelper.scheduleSyncForDataChange()
    refresh()
  }

  fun setUserLoginListingMode(userLoginListingMode: UserLoginPrivacyValues.UserLoginListingMode) {
    SignalStore.userLoginPrivacy().userLoginListingMode = userLoginListingMode
    // StorageSyncHelper.scheduleSyncForDataChange()
    ApplicationDependencies.getJobManager().add(RefreshAttributesJob())
    refresh()
  }

  fun setIncognitoKeyboard(enabled: Boolean) {
    sharedPreferences.edit().putBoolean(TextSecurePreferences.INCOGNITO_KEYBORAD_PREF, enabled).apply()
    refresh()
  }

  fun setObsoletePasswordTimeoutEnabled(enabled: Boolean) {
    sharedPreferences.edit().putBoolean(TextSecurePreferences.PASSPHRASE_TIMEOUT_PREF, enabled).apply()
    refresh()
  }

  fun setObsoletePasswordTimeout(minutes: Int) {
    TextSecurePreferences.setPassphraseTimeoutInterval(ApplicationDependencies.getApplication(), minutes)
    refresh()
  }

  fun refresh() {
    store.update(this::updateState)
  }

  private fun getState(): PrivacySettingsState {
    return PrivacySettingsState(
      blockedCount = 0,
      readReceipts = TextSecurePreferences.isReadReceiptsEnabled(ApplicationDependencies.getApplication()),
      typingIndicators = TextSecurePreferences.isTypingIndicatorsEnabled(ApplicationDependencies.getApplication()),
      screenLock = TextSecurePreferences.isScreenLockEnabled(ApplicationDependencies.getApplication()),
      screenLockActivityTimeout = TextSecurePreferences.getScreenLockTimeout(ApplicationDependencies.getApplication()),
      screenSecurity = TextSecurePreferences.isScreenSecurityEnabled(ApplicationDependencies.getApplication()),
      incognitoKeyboard = TextSecurePreferences.isIncognitoKeyboardEnabled(ApplicationDependencies.getApplication()),
      seeMyUserLogin = SignalStore.userLoginPrivacy().userLoginSharingMode,
      findMeByUserLogin = SignalStore.userLoginPrivacy().userLoginListingMode,
      isObsoletePasswordEnabled = !TextSecurePreferences.isPasswordDisabled(ApplicationDependencies.getApplication()),
      isObsoletePasswordTimeoutEnabled = TextSecurePreferences.isPassphraseTimeoutEnabled(ApplicationDependencies.getApplication()),
      obsoletePasswordTimeout = TextSecurePreferences.getPassphraseTimeoutInterval(ApplicationDependencies.getApplication())
    )
  }

  private fun updateState(state: PrivacySettingsState): PrivacySettingsState {
    return getState().copy(blockedCount = state.blockedCount)
  }

  class Factory(
    private val sharedPreferences: SharedPreferences,
    private val repository: PrivacySettingsRepository
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
      return requireNotNull(modelClass.cast(PrivacySettingsViewModel(sharedPreferences, repository)))
    }
  }
}