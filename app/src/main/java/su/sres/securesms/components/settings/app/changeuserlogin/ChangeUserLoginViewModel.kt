package su.sres.securesms.components.settings.app.changeuserlogin

import android.app.Application
import androidx.annotation.WorkerThread
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner

import io.reactivex.rxjava3.core.Single
import su.sres.core.util.logging.Log
import su.sres.securesms.dependencies.ApplicationDependencies
import su.sres.securesms.keyvalue.SignalStore
import su.sres.securesms.registration.VerifyAccountRepository
import su.sres.securesms.registration.VerifyAccountResponseProcessor
import su.sres.securesms.registration.VerifyAccountResponseWithoutKbs
import su.sres.securesms.registration.VerifyProcessor
import su.sres.securesms.registration.viewmodel.BaseRegistrationViewModel
import su.sres.securesms.util.DefaultValueLiveData
import su.sres.securesms.util.TextSecurePreferences
import su.sres.signalservice.internal.ServiceResponse
import su.sres.signalservice.internal.push.VerifyAccountResponse
import java.util.Objects

private val TAG: String = Log.tag(ChangeUserLoginViewModel::class.java)

class ChangeUserLoginViewModel(
  private val localLogin: String,
  private val changeUserLoginRepository: ChangeUserLoginRepository,
  savedState: SavedStateHandle,
  password: String,
  verifyAccountRepository: VerifyAccountRepository
) : BaseRegistrationViewModel(savedState, verifyAccountRepository,password) {

  var oldLoginState: String = ""
    private set

  private val liveOldLoginState = DefaultValueLiveData(oldLoginState)
  private val liveNewLoginState = DefaultValueLiveData(userLogin)

  fun getLiveOldLogin(): LiveData<String> {
    return liveOldLoginState
  }

  fun getLiveNewLogin(): LiveData<String> {
    return liveNewLoginState
  }

  fun setOldUserLogin(login: String) {
    oldLoginState = login

    liveOldLoginState.value = oldLoginState
  }

  fun setNewUserLogin(login: String) {
    setUserLogin(login)

    liveNewLoginState.value = this.userLogin
  }

  fun canContinue(): ContinueStatus {
    return if (oldLoginState == localLogin) {
        ContinueStatus.CAN_CONTINUE
    } else {
      ContinueStatus.OLD_NUMBER_DOESNT_MATCH
    }
  }

  override fun verifyCodeWithoutRegistrationLock(code: String): Single<VerifyAccountResponseProcessor> {
    return super.verifyCodeWithoutRegistrationLock(code)
      .doOnSubscribe { SignalStore.misc().lockChangeLogin() }
      .flatMap(this::attemptToUnlockChangeNumber)
  }

  private fun <T : VerifyProcessor> attemptToUnlockChangeNumber(processor: T): Single<T> {
    return if (processor.hasResult() || processor.isServerSentError()) {
      SignalStore.misc().unlockChangeLogin()
      Single.just(processor)
    } else {
      changeUserLoginRepository.whoAmI()
        .map { whoAmI ->
          if (Objects.equals(whoAmI.userLogin, localLogin)) {
            Log.i(TAG, "Local and remote numbers match, we can unlock.")
            SignalStore.misc().unlockChangeLogin()
          }
          processor
        }
        .onErrorReturn { processor }
    }
  }

  override fun verifyAccountWithoutRegistrationLock(): Single<ServiceResponse<VerifyAccountResponse>> {
    return changeUserLoginRepository.changeLogin(textCodeEntered, userLogin)
  }

  @WorkerThread
  override fun onVerifySuccess(processor: VerifyAccountResponseProcessor): Single<VerifyAccountResponseProcessor> {
    return changeUserLoginRepository.changeLocalLogin(userLogin)
      .map { processor }
      .onErrorReturn { t ->
        Log.w(TAG, "Error attempting to change local login", t)
        VerifyAccountResponseWithoutKbs(ServiceResponse.forUnknownError(t))
      }
  }

  class Factory(owner: SavedStateRegistryOwner) : AbstractSavedStateViewModelFactory(owner, null) {

    override fun <T : ViewModel?> create(key: String, modelClass: Class<T>, handle: SavedStateHandle): T {
      val context: Application = ApplicationDependencies.getApplication()
      val localLogin: String = SignalStore.account().userLogin!!
      val password: String = SignalStore.account().servicePassword!!

      val viewModel = ChangeUserLoginViewModel(
        localLogin = localLogin,
        changeUserLoginRepository = ChangeUserLoginRepository(context),
        savedState = handle,
        password = password,
        verifyAccountRepository = VerifyAccountRepository(context)
      )

      return requireNotNull(modelClass.cast(viewModel))
    }
  }

  enum class ContinueStatus {
    CAN_CONTINUE,
    OLD_NUMBER_DOESNT_MATCH
  }
}