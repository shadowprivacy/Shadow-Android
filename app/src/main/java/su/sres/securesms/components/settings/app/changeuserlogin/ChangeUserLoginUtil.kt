package su.sres.securesms.components.settings.app.changeuserlogin

import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import su.sres.securesms.R

/**
 * Helpers for various aspects of the change number flow.
 */
object ChangeUserLoginUtil {
  @JvmStatic
  fun getViewModel(fragment: Fragment): ChangeUserLoginViewModel {
    val navController = NavHostFragment.findNavController(fragment)
    return ViewModelProvider(
      navController.getViewModelStoreOwner(R.id.app_settings_change_login),
      ChangeUserLoginViewModel.Factory(navController.getBackStackEntry(R.id.app_settings_change_login))
    ).get(ChangeUserLoginViewModel::class.java)
  }

  // captcha off
  /* fun getCaptchaArguments(): Bundle {
    return Bundle().apply {
      putSerializable(
        CaptchaFragment.EXTRA_VIEW_MODEL_PROVIDER,
        object : CaptchaFragment.CaptchaViewModelProvider {
          override fun get(fragment: CaptchaFragment): BaseRegistrationViewModel = getViewModel(fragment)
        }
      )
    }
  } */

  fun Fragment.changeUserLoginSuccess() {
    findNavController().navigate(R.id.action_pop_app_settings_change_login)
    Toast.makeText(requireContext(), R.string.ChangeNumber__your_phone_number_has_been_changed, Toast.LENGTH_SHORT).show()
  }
}