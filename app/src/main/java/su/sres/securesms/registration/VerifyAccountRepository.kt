package su.sres.securesms.registration

import android.app.Application
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import su.sres.core.util.logging.Log
import su.sres.securesms.AppCapabilities
import su.sres.securesms.gcm.FcmUtil
import su.sres.securesms.keyvalue.SignalStore
import su.sres.securesms.push.AccountManagerFactory
import su.sres.securesms.util.TextSecurePreferences
import org.whispersystems.libsignal.util.guava.Optional
import su.sres.signalservice.api.SignalServiceAccountManager
import su.sres.signalservice.api.crypto.UnidentifiedAccess
import su.sres.signalservice.internal.ServiceResponse
import su.sres.signalservice.internal.push.RequestVerificationCodeResponse
import su.sres.signalservice.internal.push.VerifyAccountResponse
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Request verification codes to help prove ownership of a user login.
 */
class VerifyAccountRepository(private val context: Application) {

  fun requestVerificationCode(
    e164: String,
    password: String,
    mode: Mode,
    captchaToken: String? = null
  ): Single<ServiceResponse<RequestVerificationCodeResponse>> {
    Log.d(TAG, "SMS Verification requested")

    return Single.fromCallable {
      val fcmToken: Optional<String> = FcmUtil.getToken()
      val accountManager = AccountManagerFactory.createUnauthenticated(context, e164, password)
      val pushChallenge = PushChallengeRequest.getPushChallengeBlocking(accountManager, fcmToken, e164, PUSH_REQUEST_TIMEOUT)

      // captcha off
      accountManager.requestSmsVerificationCode(mode.isSmsRetrieverSupported, Optional.absent(), pushChallenge, fcmToken)

    }.subscribeOn(Schedulers.io())
  }

  fun verifyAccount(registrationData: RegistrationData): Single<ServiceResponse<VerifyAccountResponse>> {
    val universalUnidentifiedAccess: Boolean = TextSecurePreferences.isUniversalUnidentifiedAccess(context)
    val unidentifiedAccessKey: ByteArray = UnidentifiedAccess.deriveAccessKeyFrom(registrationData.profileKey)

    val accountManager: SignalServiceAccountManager = AccountManagerFactory.createUnauthenticated(
      context,
      registrationData.e164,
      registrationData.password
    )

    return Single.fromCallable {
      accountManager.verifyAccount(
        registrationData.code,
        registrationData.registrationId,
        registrationData.isNotFcm,
        unidentifiedAccessKey,
        universalUnidentifiedAccess,
        AppCapabilities.getCapabilities(true),
        SignalStore.userLoginPrivacy().userLoginListingMode.isDiscoverable
      )
    }.subscribeOn(Schedulers.io())
  }

  enum class Mode(val isSmsRetrieverSupported: Boolean) {
    SMS_WITH_LISTENER(true),
    SMS_WITHOUT_LISTENER(false),
    PHONE_CALL(false);
  }

  companion object {
    private val TAG = Log.tag(VerifyAccountRepository::class.java)
    private val PUSH_REQUEST_TIMEOUT = TimeUnit.SECONDS.toMillis(5)
  }
}