package su.sres.securesms.registration.service;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class RegistrationService {

    private final Credentials credentials;

    private RegistrationService(@NonNull Credentials credentials) {
        this.credentials = credentials;
    }

    public static RegistrationService getInstance(@NonNull String userLogin, @NonNull String password) {
        return new RegistrationService(new Credentials(userLogin, password));
    }

    /**
     * See {@link RegistrationCodeRequest}.
     */
    public void requestVerificationCode(@NonNull Activity activity,
                                        @NonNull RegistrationCodeRequest.Mode mode,
// captcha off
//                                        @Nullable String captchaToken,
                                        @NonNull RegistrationCodeRequest.SmsVerificationCodeCallback callback)
    {
    // captcha off
        //    RegistrationCodeRequest.requestSmsVerificationCode(activity, credentials, captchaToken, mode, callback);
        RegistrationCodeRequest.requestSmsVerificationCode(activity, credentials, mode, callback);
    }

    /**
     * See {@link CodeVerificationRequest}.
     */
    public void verifyAccount(@NonNull Activity activity,
                              @Nullable String fcmToken,
                              @NonNull String code,
                              @Nullable String pin,
                              @NonNull CodeVerificationRequest.VerifyCallback callback)
    {
        CodeVerificationRequest.verifyAccount(activity, credentials, fcmToken, code, pin, callback);
    }
}