package su.sres.securesms.components.settings.app.changeuserlogin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.subscribeBy
import su.sres.core.util.logging.Log
import su.sres.securesms.MainActivity
import su.sres.securesms.PassphraseRequiredActivity
import su.sres.securesms.R
import su.sres.securesms.keyvalue.SignalStore
import su.sres.securesms.logsubmit.SubmitDebugLogActivity
import su.sres.securesms.util.DynamicNoActionBarTheme
import su.sres.securesms.util.DynamicTheme
import su.sres.securesms.util.LifecycleDisposable
import su.sres.securesms.util.TextSecurePreferences
import java.util.Objects

private val TAG: String = Log.tag(ChangeLoginLockActivity::class.java)

/**
 * A captive activity that can determine if an interrupted/erred change login request
 * caused a disparity between the server and our locally stored login.
 */
class ChangeLoginLockActivity : PassphraseRequiredActivity() {

  private val dynamicTheme: DynamicTheme = DynamicNoActionBarTheme()
  private val disposables: LifecycleDisposable = LifecycleDisposable()
  private lateinit var changeLoginRepository: ChangeUserLoginRepository

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    dynamicTheme.onCreate(this)
    disposables.bindTo(lifecycle)

    setContentView(R.layout.activity_change_number_lock)

    changeLoginRepository = ChangeUserLoginRepository(applicationContext)
    checkWhoAmI()
  }

  override fun onResume() {
    super.onResume()
    dynamicTheme.onResume(this)
  }

  override fun onBackPressed() = Unit

  private fun checkWhoAmI() {
    disposables.add(
      changeLoginRepository.whoAmI()
        .flatMap { whoAmI ->
          if (Objects.equals(whoAmI.userLogin, SignalStore.account().userLogin)) {
            Log.i(TAG, "Local and remote logins match, nothing needs to be done.")
            Single.just(false)
          } else {
            Log.i(TAG, "Local (${SignalStore.account().userLogin}) and remote (${whoAmI.userLogin}) logins do not match, updating local.")
            changeLoginRepository.changeLocalLogin(whoAmI.userLogin)
              .map { true }
          }
        }
        .observeOn(AndroidSchedulers.mainThread())
        .subscribeBy(onSuccess = { onChangeStatusConfirmed() }, onError = this::onFailedToGetChangeNumberStatus)
    )
  }

  private fun onChangeStatusConfirmed() {
    SignalStore.misc().unlockChangeLogin()

    MaterialAlertDialogBuilder(this)
      .setTitle(R.string.ChangeNumberLockActivity__change_status_confirmed)
      .setMessage(getString(R.string.ChangeNumberLockActivity__your_number_has_been_confirmed_as_s, SignalStore.account().userLogin!!))
      .setPositiveButton(android.R.string.ok) { _, _ ->
        startActivity(MainActivity.clearTop(this))
        finish()
      }
      .setCancelable(false)
      .show()
  }

  private fun onFailedToGetChangeNumberStatus(error: Throwable) {
    Log.w(TAG, "Unable to determine status of change number", error)

    MaterialAlertDialogBuilder(this)
      .setTitle(R.string.ChangeNumberLockActivity__change_status_unconfirmed)
      .setMessage(getString(R.string.ChangeNumberLockActivity__we_could_not_determine_the_status_of_your_change_number_request, error.javaClass.simpleName))
      .setPositiveButton(R.string.ChangeNumberLockActivity__retry) { _, _ -> checkWhoAmI() }
      .setNegativeButton(R.string.ChangeNumberLockActivity__leave) { _, _ -> finish() }
      .setNeutralButton(R.string.ChangeNumberLockActivity__submit_debug_log) { _, _ ->
        startActivity(Intent(this, SubmitDebugLogActivity::class.java))
        finish()
      }
      .setCancelable(false)
      .show()
  }

  companion object {
    @JvmStatic
    fun createIntent(context: Context): Intent {
      return Intent(context, ChangeLoginLockActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
      }
    }
  }
}