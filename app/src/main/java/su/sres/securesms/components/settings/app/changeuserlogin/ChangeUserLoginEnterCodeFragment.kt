package su.sres.securesms.components.settings.app.changeuserlogin

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.Toolbar
import androidx.navigation.fragment.findNavController
import su.sres.securesms.R
import su.sres.securesms.components.settings.app.changeuserlogin.ChangeUserLoginUtil.changeUserLoginSuccess
import su.sres.securesms.components.settings.app.changeuserlogin.ChangeUserLoginUtil.getViewModel
import su.sres.securesms.keyvalue.SignalStore
import su.sres.securesms.registration.fragments.BaseEnterCodeFragment

class ChangeNumberEnterCodeFragment : BaseEnterCodeFragment<ChangeUserLoginViewModel>(R.layout.fragment_change_login_enter_code) {

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    val toolbar: Toolbar = view.findViewById(R.id.toolbar)
    toolbar.title = viewModel.userLogin
    toolbar.setNavigationOnClickListener { navigateUp() }

    view.findViewById<View>(R.id.verify_header).setOnClickListener(null)

    requireActivity().onBackPressedDispatcher.addCallback(
      viewLifecycleOwner,
      object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          navigateUp()
        }
      }
    )
  }

  private fun navigateUp() {
    if (SignalStore.misc().isChangeLoginLocked) {
      startActivity(ChangeLoginLockActivity.createIntent(requireContext()))
    } else {
      findNavController().navigateUp()
    }
  }

  override fun getViewModel(): ChangeUserLoginViewModel {
    return getViewModel(this)
  }

  override fun handleSuccessfulVerify() {
    displaySuccess { changeUserLoginSuccess() }
  }

  // captcha off
  /* override fun navigateToCaptcha() {
    findNavController().navigate(R.id.action_changeNumberEnterCodeFragment_to_captchaFragment, getCaptchaArguments())
  } */

  /* override fun navigateToRegistrationLock(timeRemaining: Long) {
    findNavController().navigate(ChangeNumberEnterCodeFragmentDirections.actionChangeNumberEnterCodeFragmentToChangeNumberRegistrationLock(timeRemaining))
  }

  override fun navigateToKbsAccountLocked() {
    findNavController().navigate(ChangeNumberEnterCodeFragmentDirections.actionChangeNumberEnterCodeFragmentToChangeNumberAccountLocked())
  } */
}