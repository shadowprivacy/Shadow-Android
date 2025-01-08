package su.sres.securesms.components.settings.app.changeuserlogin

import android.content.Context
import androidx.annotation.WorkerThread
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import su.sres.core.util.logging.Log
import su.sres.securesms.database.ShadowDatabase
import su.sres.securesms.dependencies.ApplicationDependencies
import su.sres.securesms.keyvalue.CertificateType
import su.sres.securesms.keyvalue.SignalStore
import su.sres.signalservice.internal.ServiceResponse
import su.sres.signalservice.internal.push.VerifyAccountResponse
import su.sres.signalservice.internal.push.WhoAmIResponse

private val TAG: String = Log.tag(ChangeUserLoginRepository::class.java)

class ChangeUserLoginRepository(private val context: Context) {

  private val accountManager = ApplicationDependencies.getSignalServiceAccountManager()

  fun changeLogin(code: String, newLogin: String): Single<ServiceResponse<VerifyAccountResponse>> {
    return Single.fromCallable { accountManager.changeUserLogin(code, newLogin) }
      .subscribeOn(Schedulers.io())
  }

  @Suppress("UsePropertyAccessSyntax")
  fun whoAmI(): Single<WhoAmIResponse> {
    return Single.fromCallable { ApplicationDependencies.getSignalServiceAccountManager().getWhoAmI() }
      .subscribeOn(Schedulers.io())
  }

  @WorkerThread
  fun changeLocalLogin(login: String): Single<Unit> {
    ShadowDatabase.recipients.updateSelfLogin(login)

    SignalStore.account().setUserLogin(login)

    ApplicationDependencies.closeConnections()
    ApplicationDependencies.getIncomingMessageObserver()

    return rotateCertificates()
  }

  @Suppress("UsePropertyAccessSyntax")
  private fun rotateCertificates(): Single<Unit> {
    val certificateTypes = SignalStore.userLoginPrivacy().allCertificateTypes

    Log.i(TAG, "Rotating these certificates $certificateTypes")

    return Single.fromCallable {
      for (certificateType in certificateTypes) {
        val certificate: ByteArray? = when (certificateType) {
          CertificateType.UUID_AND_E164 -> accountManager.getSenderCertificate()
          CertificateType.UUID_ONLY -> accountManager.getSenderCertificateForUserLoginPrivacy()
          else -> throw AssertionError()
        }

        Log.i(TAG, "Successfully got $certificateType certificate")

        SignalStore.certificateValues().setUnidentifiedAccessCertificate(certificateType, certificate)
      }
    }.subscribeOn(Schedulers.io())
  }
}