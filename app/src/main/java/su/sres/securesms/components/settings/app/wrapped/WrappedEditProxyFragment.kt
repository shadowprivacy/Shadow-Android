package su.sres.securesms.components.settings.app.wrapped

import androidx.fragment.app.Fragment
import su.sres.securesms.R
import su.sres.securesms.preferences.EditProxyFragment

class WrappedEditProxyFragment : SettingsWrapperFragment() {
  override fun getFragment(): Fragment {
    toolbar.setTitle(R.string.preferences_use_proxy)
    return EditProxyFragment()
  }
}