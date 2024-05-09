package su.sres.securesms.components.settings.app.changeuserlogin

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.navigation.fragment.findNavController
import su.sres.securesms.LoggingFragment
import su.sres.securesms.R

class ChangeNumberConfirmFragment : LoggingFragment(R.layout.fragment_change_login_confirm) {
  private lateinit var viewModel: ChangeUserLoginViewModel

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    viewModel = ChangeUserLoginUtil.getViewModel(this)

    val toolbar: Toolbar = view.findViewById(R.id.toolbar)
    toolbar.setTitle(R.string.ChangeNumberEnterPhoneNumberFragment__change_number)
    toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

    val confirmMessage: TextView = view.findViewById(R.id.change_login_confirm_new_login_message)
    confirmMessage.text = getString(R.string.ChangeNumberConfirmFragment__you_are_about_to_change_your_phone_number_from_s_to_s, viewModel.oldLoginState, viewModel.userLogin)

    val newLogin: TextView = view.findViewById(R.id.change_login_confirm_new_login)
    newLogin.text = viewModel.userLogin

    val editLogin: View = view.findViewById(R.id.change_login_confirm_edit_login)
    editLogin.setOnClickListener { findNavController().navigateUp() }

    val changeLogin: View = view.findViewById(R.id.change_login_confirm_change_login)
    changeLogin.setOnClickListener { findNavController().navigate(R.id.action_changeUserLoginConfirmFragment_to_changeUserLoginVerifyFragment) }
  }
}