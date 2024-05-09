package su.sres.securesms.components.settings.app.changeuserlogin

import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import su.sres.securesms.LoggingFragment
import su.sres.securesms.R
import su.sres.securesms.components.LabeledEditText
import su.sres.securesms.components.settings.app.changeuserlogin.ChangeUserLoginUtil.getViewModel
import su.sres.securesms.components.settings.app.changeuserlogin.ChangeUserLoginViewModel.ContinueStatus
import su.sres.securesms.registration.util.RegistrationUserLoginInputController
import su.sres.securesms.util.Dialogs

class ChangeNumberEnterPhoneNumberFragment : LoggingFragment(R.layout.fragment_change_login_enter_user_login) {

  private lateinit var scrollView: ScrollView

  private lateinit var oldLogin: LabeledEditText
  private lateinit var newLogin: LabeledEditText

  private lateinit var viewModel: ChangeUserLoginViewModel

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    viewModel = getViewModel(this)

    val toolbar: Toolbar = view.findViewById(R.id.toolbar)
    toolbar.setTitle(R.string.ChangeNumberEnterPhoneNumberFragment__change_number)
    toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

    view.findViewById<View>(R.id.change_login_enter_user_login_continue).setOnClickListener {
      onContinue()
    }

    scrollView = view.findViewById(R.id.change_login_enter_user_login_scroll)

    oldLogin = view.findViewById(R.id.change_login_enter_user_login_old_login_login)

    val oldController = RegistrationUserLoginInputController(
      requireContext(),
      oldLogin,
      false,
      object : RegistrationUserLoginInputController.Callbacks {
        override fun onLoginFocused() {
          scrollView.postDelayed({ scrollView.smoothScrollTo(0, oldLogin.bottom) }, 250)
        }

        override fun onLoginInputNext(view: View) {
        }

        override fun onLoginInputDone(view: View) = Unit

        override fun setUserLogin(login: String) {
          viewModel.setOldUserLogin(login)
        }
      }
    )
    newLogin = view.findViewById(R.id.change_login_enter_user_login_new_login_login)

    val newController = RegistrationUserLoginInputController(
      requireContext(),
      newLogin,
      true,
      object : RegistrationUserLoginInputController.Callbacks {
        override fun onLoginFocused() {
          scrollView.postDelayed({ scrollView.smoothScrollTo(0, newLogin.bottom) }, 250)
        }

        override fun onLoginInputNext(view: View) = Unit

        override fun onLoginInputDone(view: View) {
          onContinue()
        }

        override fun setUserLogin(login: String) {
          viewModel.setNewUserLogin(login)
        }
      }
    )

    viewModel.getLiveOldLogin().observe(viewLifecycleOwner, oldController::updateUserLogin)
    viewModel.getLiveNewLogin().observe(viewLifecycleOwner, newController::updateUserLogin)
  }

  private fun onContinue() {

    if (TextUtils.isEmpty(oldLogin.text)) {
      Toast.makeText(context, getString(R.string.ChangeNumberEnterPhoneNumberFragment__you_must_specify_your_old_phone_number), Toast.LENGTH_LONG).show()
      return
    }

    if (TextUtils.isEmpty(newLogin.text)) {
      Toast.makeText(context, getString(R.string.ChangeNumberEnterPhoneNumberFragment__you_must_specify_your_new_phone_number), Toast.LENGTH_LONG).show()
      return
    }

    when (viewModel.canContinue()) {
      ContinueStatus.CAN_CONTINUE -> findNavController().navigate(R.id.action_enterUserLoginChangeFragment_to_changeUserLoginConfirmFragment)
      ContinueStatus.OLD_NUMBER_DOESNT_MATCH -> {
        MaterialAlertDialogBuilder(requireContext())
          .setMessage(R.string.ChangeNumberEnterPhoneNumberFragment__the_phone_number_you_entered_doesnt_match_your_accounts)
          .setPositiveButton(android.R.string.ok, null)
          .show()
      }
    }
  }
}