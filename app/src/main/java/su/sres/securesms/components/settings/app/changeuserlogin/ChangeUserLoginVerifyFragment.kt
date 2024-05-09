package su.sres.securesms.components.settings.app.changeuserlogin

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.navigation.fragment.findNavController
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import su.sres.core.util.logging.Log
import su.sres.securesms.LoggingFragment
import su.sres.securesms.R
// import su.sres.securesms.components.settings.app.changeuserlogin.ChangeUserLoginUtil.getCaptchaArguments
import su.sres.securesms.components.settings.app.changeuserlogin.ChangeUserLoginUtil.getViewModel
import su.sres.securesms.registration.VerifyAccountRepository
import su.sres.securesms.util.LifecycleDisposable

private val TAG: String = Log.tag(ChangeNumberVerifyFragment::class.java)

class ChangeNumberVerifyFragment : LoggingFragment(R.layout.fragment_change_user_login_verify) {
  private lateinit var viewModel: ChangeUserLoginViewModel

  private var requestingCaptcha: Boolean = false

  private val lifecycleDisposable: LifecycleDisposable = LifecycleDisposable()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    lifecycleDisposable.bindTo(lifecycle)
    viewModel = getViewModel(this)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val toolbar: Toolbar = view.findViewById(R.id.toolbar)
    toolbar.setTitle(R.string.ChangeNumberVerifyFragment__change_number)
    toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

    val status: TextView = view.findViewById(R.id.change_user_login_verify_status)
    status.text = getString(R.string.ChangeNumberVerifyFragment__verifying_s, viewModel.userLogin)

    if (!requestingCaptcha || viewModel.hasCaptchaToken()) {
      requestCode()
    } else {
      Toast.makeText(requireContext(), R.string.ChangeNumberVerifyFragment__captcha_required, Toast.LENGTH_SHORT).show()
      findNavController().navigateUp()
    }
  }

  private fun requestCode() {
    lifecycleDisposable.add(
      viewModel.requestVerificationCode(VerifyAccountRepository.Mode.SMS_WITHOUT_LISTENER)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe { processor ->
          if (processor.hasResult()) {
            findNavController().navigate(R.id.action_changeUserLoginVerifyFragment_to_changeUserLoginEnterCodeFragment)
          } else if (processor.localRateLimit()) {
            Log.i(TAG, "Unable to request verification code due to local rate limit")
            findNavController().navigate(R.id.action_changeUserLoginVerifyFragment_to_changeUserLoginEnterCodeFragment)
          // captcha off
          /* } else if (processor.captchaRequired()) {
            Log.i(TAG, "Unable to request sms code due to captcha required")
            findNavController().navigate(R.id.action_changeUserLoginVerifyFragment_to_captchaFragment, getCaptchaArguments())
            requestingCaptcha = true */
          } else if (processor.rateLimit()) {
            Log.i(TAG, "Unable to request verification code due to rate limit")
            Toast.makeText(requireContext(), R.string.RegistrationActivity_rate_limited_to_service, Toast.LENGTH_LONG).show()
            findNavController().navigateUp()
          } else {
            Log.w(TAG, "Unable to request verification code", processor.error)
            Toast.makeText(requireContext(), R.string.RegistrationActivity_unable_to_connect_to_service, Toast.LENGTH_LONG).show()
            findNavController().navigateUp()
          }
        }
    )
  }
}