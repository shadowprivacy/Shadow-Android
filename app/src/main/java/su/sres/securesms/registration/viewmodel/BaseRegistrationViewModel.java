package su.sres.securesms.registration.viewmodel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import su.sres.securesms.registration.RequestVerificationCodeResponseProcessor;
import su.sres.securesms.registration.VerifyAccountRepository;
import su.sres.securesms.registration.VerifyAccountRepository.Mode;
import su.sres.securesms.registration.VerifyAccountResponseProcessor;
import su.sres.securesms.registration.VerifyAccountResponseWithoutKbs;
import su.sres.signalservice.internal.ServiceResponse;
import su.sres.signalservice.internal.push.VerifyAccountResponse;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;

/**
 * Base view model used in registration and change number flow. Handles the storage of all data
 * shared between the two flows, orchestrating verification, and calling to subclasses to peform
 * the specific verify operations for each flow.
 */
public abstract class BaseRegistrationViewModel extends ViewModel {

  private static final String STATE_USER_LOGIN                       = "USER_LOGIN";
  private static final String STATE_REGISTRATION_SECRET              = "REGISTRATION_SECRET";
  private static final String STATE_VERIFICATION_CODE                = "TEXT_CODE_ENTERED";
  private static final String STATE_CAPTCHA                          = "CAPTCHA";
  private static final String STATE_SUCCESSFUL_CODE_REQUEST_ATTEMPTS = "SUCCESSFUL_CODE_REQUEST_ATTEMPTS";
  private static final String STATE_REQUEST_RATE_LIMITER             = "REQUEST_RATE_LIMITER";

  protected final SavedStateHandle        savedState;
  protected final VerifyAccountRepository verifyAccountRepository;

  public BaseRegistrationViewModel(@NonNull SavedStateHandle savedStateHandle,
                                   @NonNull VerifyAccountRepository verifyAccountRepository,
                                   @NonNull String password)
  {
    this.savedState = savedStateHandle;

    this.verifyAccountRepository = verifyAccountRepository;

    setInitialDefaultValue(STATE_USER_LOGIN, "");
    setInitialDefaultValue(STATE_REGISTRATION_SECRET, password);
    setInitialDefaultValue(STATE_VERIFICATION_CODE, "");
    setInitialDefaultValue(STATE_SUCCESSFUL_CODE_REQUEST_ATTEMPTS, 0);
    setInitialDefaultValue(STATE_REQUEST_RATE_LIMITER, new LocalCodeRequestRateLimiter(60_000));
  }

  protected <T> void setInitialDefaultValue(@NonNull String key, @NonNull T initialValue) {
    if (!savedState.contains(key) || savedState.get(key) == null) {
      savedState.set(key, initialValue);
    }
  }

  public @NonNull String getUserLogin() {
    //noinspection ConstantConditions
    return savedState.get(STATE_USER_LOGIN);
  }

  public @NonNull LiveData<String> getLiveUserLogin() {
    return savedState.getLiveData(STATE_USER_LOGIN);
  }

  public void setUserLogin(String userLogin) {
    setViewState(userLogin);
  }

  protected void setViewState(String userLogin) {
    if (!userLogin.equals(getUserLogin())) {
      savedState.set(STATE_USER_LOGIN, userLogin);
    }
  }

  public @NonNull String getRegistrationSecret() {
    //noinspection ConstantConditions
    return savedState.get(STATE_REGISTRATION_SECRET);
  }

  public @NonNull String getTextCodeEntered() {
    //noinspection ConstantConditions
    return savedState.get(STATE_VERIFICATION_CODE);
  }

  public @Nullable String getCaptchaToken() {
    return savedState.get(STATE_CAPTCHA);
  }

  public boolean hasCaptchaToken() {
    return getCaptchaToken() != null;
  }

  public void setCaptchaResponse(@Nullable String captchaToken) {
    savedState.set(STATE_CAPTCHA, captchaToken);
  }

  public void clearCaptchaResponse() {
    setCaptchaResponse(null);
  }

  public void onVerificationCodeEntered(String code) {
    savedState.set(STATE_VERIFICATION_CODE, code);
  }

  public void markASuccessfulAttempt() {
    //noinspection ConstantConditions
    savedState.set(STATE_SUCCESSFUL_CODE_REQUEST_ATTEMPTS, (Integer) savedState.get(STATE_SUCCESSFUL_CODE_REQUEST_ATTEMPTS) + 1);
  }

  public LiveData<Integer> getSuccessfulCodeRequestAttempts() {
    return savedState.getLiveData(STATE_SUCCESSFUL_CODE_REQUEST_ATTEMPTS, 0);
  }

  public @NonNull LocalCodeRequestRateLimiter getRequestLimiter() {
    //noinspection ConstantConditions
    return savedState.get(STATE_REQUEST_RATE_LIMITER);
  }

  public void updateLimiter() {
    savedState.set(STATE_REQUEST_RATE_LIMITER, savedState.get(STATE_REQUEST_RATE_LIMITER));
  }

  public Single<RequestVerificationCodeResponseProcessor> requestVerificationCode(@NonNull Mode mode) {
    String captcha = getCaptchaToken();
    clearCaptchaResponse();

    if (!getRequestLimiter().canRequest(mode, getUserLogin(), System.currentTimeMillis())) {
      return Single.just(RequestVerificationCodeResponseProcessor.forLocalRateLimit());
    }

    return verifyAccountRepository.requestVerificationCode(getUserLogin(),
                                                           getRegistrationSecret(),
                                                           mode,
                                                           captcha)
                                  .map(RequestVerificationCodeResponseProcessor::new)
                                  .observeOn(AndroidSchedulers.mainThread())
                                  .doOnSuccess(processor -> {
                                    if (processor.hasResult()) {
                                      markASuccessfulAttempt();
                                      getRequestLimiter().onSuccessfulRequest(mode, getUserLogin(), System.currentTimeMillis());
                                    } else {
                                      getRequestLimiter().onUnsuccessfulRequest();
                                    }
                                    updateLimiter();
                                  });
  }

  public Single<VerifyAccountResponseProcessor> verifyCodeWithoutRegistrationLock(@NonNull String code) {
    onVerificationCodeEntered(code);

    return verifyAccountWithoutRegistrationLock()
        .map(VerifyAccountResponseWithoutKbs::new)
        .flatMap(processor -> {
          if (processor.hasResult()) {
            return onVerifySuccess(processor);
          }
          return Single.just(processor);
        })
        // .observeOn(AndroidSchedulers.mainThread())
        // .doOnSuccess(processor -> {
        //  if (processor.registrationLock() && !processor.isKbsLocked()) {
        //    setLockedTimeRemaining(processor.getLockedException().getTimeRemaining());
        //    setKeyBackupTokenData(processor.getTokenData());
        // })
        ;
  }


  protected abstract Single<ServiceResponse<VerifyAccountResponse>> verifyAccountWithoutRegistrationLock();

  protected abstract Single<VerifyAccountResponseProcessor> onVerifySuccess(@NonNull VerifyAccountResponseProcessor processor);
}
