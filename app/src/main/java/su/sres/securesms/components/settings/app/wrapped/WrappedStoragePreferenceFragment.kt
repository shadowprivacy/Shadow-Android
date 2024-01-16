package su.sres.securesms.components.settings.app.wrapped

import androidx.fragment.app.Fragment
import su.sres.securesms.preferences.StoragePreferenceFragment

class WrappedStoragePreferenceFragment : SettingsWrapperFragment() {
  override fun getFragment(): Fragment {
    return StoragePreferenceFragment()
  }
}