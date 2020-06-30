package su.sres.securesms.registration.viewmodel;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import su.sres.securesms.util.Util;
// import su.sres.securesms.util.livedata.LiveDataPair;

public final class RegistrationViewModel extends ViewModel {

    private final String                           secret;
    private final MutableLiveData<String>          userLogin;
    private final MutableLiveData<String>          textCodeEntered;
// captcha off
    //    private final MutableLiveData<String>          captchaToken;
    private final MutableLiveData<String>          fcmToken;
    private final MutableLiveData<Boolean>         restoreFlowShown;
    private final MutableLiveData<Integer>         successfulCodeRequestAttempts;
    private final MutableLiveData<LocalCodeRequestRateLimiter> requestLimiter;

    public RegistrationViewModel(@NonNull SavedStateHandle savedStateHandle) {
        secret = loadValue(savedStateHandle, "REGISTRATION_SECRET", Util.getSecret(18));

        userLogin                     = savedStateHandle.getLiveData("USER_LOGIN", "");
        textCodeEntered               = savedStateHandle.getLiveData("TEXT_CODE_ENTERED", "");
// captcha off
        //        captchaToken                  = savedStateHandle.getLiveData("CAPTCHA");
        fcmToken                      = savedStateHandle.getLiveData("FCM_TOKEN");
        restoreFlowShown              = savedStateHandle.getLiveData("RESTORE_FLOW_SHOWN", false);
        successfulCodeRequestAttempts = savedStateHandle.getLiveData("SUCCESSFUL_CODE_REQUEST_ATTEMPTS", 0);
        requestLimiter                = savedStateHandle.getLiveData("REQUEST_RATE_LIMITER", new LocalCodeRequestRateLimiter(60_000));
    }

    private static <T> T loadValue(@NonNull SavedStateHandle savedStateHandle, @NonNull String key, @NonNull T initialValue) {
        if (!savedStateHandle.contains(key)) {
            savedStateHandle.set(key, initialValue);
        }
        return savedStateHandle.get(key);
    }

    public @NonNull String getUserLogin() {
        //noinspection ConstantConditions Live data was given an initial value
        return userLogin.getValue();
    }

    public @NonNull LiveData<String> getLiveUserLogin() {
        return userLogin;
    }

    public @NonNull String getTextCodeEntered() {
        //noinspection ConstantConditions Live data was given an initial value
        return textCodeEntered.getValue();
    }

    // captcha off
/*     public String getCaptchaToken() {
        return captchaToken.getValue();
    }

    public boolean hasCaptchaToken() {
        return getCaptchaToken() != null;
    }  */

    public String getRegistrationSecret() {
        return secret;
    }

    // captcha off
/*    public void onCaptchaResponse(String captchaToken) {
        this.captchaToken.setValue(captchaToken);
    }

    public void clearCaptchaResponse() {
        captchaToken.setValue(null);
    }  */

/*    public void onCountrySelected(@Nullable String selectedCountryName, int countryCode) {
        setViewState(getNumber().toBuilder()
                .selectedCountryDisplayName(selectedCountryName)
                .countryCode(countryCode).build());
    } */


    public void setViewState(String userLoginViewState) {
        if (!userLoginViewState.equals(getUserLogin())) {
            userLogin.setValue(userLoginViewState);
        }
    }

    @MainThread
    public void onVerificationCodeEntered(String code) {
        textCodeEntered.setValue(code);
    }

/*    public void onNumberDetected(int countryCode, long nationalNumber) {
        setViewState(getNumber().toBuilder()
                .countryCode(countryCode)
                .nationalNumber(nationalNumber)
                .build());
    } */

    public String getFcmToken() {
        return fcmToken.getValue();
    }

    @MainThread
    public void setFcmToken(@Nullable String fcmToken) {
        this.fcmToken.setValue(fcmToken);
    }

    public void setWelcomeSkippedOnRestore() {
        restoreFlowShown.setValue(true);
    }

    public boolean hasRestoreFlowBeenShown() {
        //noinspection ConstantConditions Live data was given an initial value
        return restoreFlowShown.getValue();
    }

    public void markASuccessfulAttempt() {
        //noinspection ConstantConditions Live data was given an initial value
        successfulCodeRequestAttempts.setValue(successfulCodeRequestAttempts.getValue() + 1);
    }

    public LiveData<Integer> getSuccessfulCodeRequestAttempts() {
        return successfulCodeRequestAttempts;
    }

    public @NonNull LocalCodeRequestRateLimiter getRequestLimiter() {
        //noinspection ConstantConditions Live data was given an initial value
        return requestLimiter.getValue();
    }

    public void updateLimiter() {
        requestLimiter.setValue(requestLimiter.getValue());
    }
}