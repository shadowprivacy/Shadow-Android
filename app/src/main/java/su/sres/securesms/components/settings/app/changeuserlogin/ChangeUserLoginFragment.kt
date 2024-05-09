package su.sres.securesms.components.settings.app.changeuserlogin

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.navigation.fragment.findNavController
import su.sres.securesms.LoggingFragment
import su.sres.securesms.R

class ChangeNumberFragment : LoggingFragment(R.layout.fragment_change_user_login) {
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val toolbar: Toolbar = view.findViewById(R.id.toolbar)
    toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

    view.findViewById<View>(R.id.change_user_login_continue).setOnClickListener {
      findNavController().navigate(R.id.action_changeUserLoginFragment_to_enterUserLoginChangeFragment)
    }
  }
}