package su.sres.securesms.components.settings.app.wrapped

import androidx.fragment.app.Fragment
import su.sres.securesms.R
import su.sres.securesms.preferences.BackupsPreferenceFragment

class WrappedBackupsPreferenceFragment : SettingsWrapperFragment() {
  override fun getFragment(): Fragment {
    toolbar.setTitle(R.string.BackupsPreferenceFragment__chat_backups)
    return BackupsPreferenceFragment()
  }
}