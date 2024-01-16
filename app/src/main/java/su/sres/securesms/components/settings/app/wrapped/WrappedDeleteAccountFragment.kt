package su.sres.securesms.components.settings.app.wrapped

import androidx.fragment.app.Fragment
import su.sres.securesms.R
import su.sres.securesms.delete.DeleteAccountFragment

class WrappedDeleteAccountFragment : SettingsWrapperFragment() {
  override fun getFragment(): Fragment {
    toolbar.setTitle(R.string.preferences__delete_account)
    return DeleteAccountFragment()
  }
}