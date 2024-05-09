package su.sres.securesms.registration

import su.sres.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException
import su.sres.signalservice.internal.ServiceResponse
import su.sres.signalservice.internal.ServiceResponseProcessor
import su.sres.signalservice.internal.push.LockedException
import su.sres.signalservice.internal.push.VerifyAccountResponse

/**
 * Process responses from attempting to verify an account for use in account registration.
 */
sealed class VerifyAccountResponseProcessor(
  response: ServiceResponse<VerifyAccountResponse>
) : ServiceResponseProcessor<VerifyAccountResponse>(response), VerifyProcessor {

  public override fun authorizationFailed(): Boolean {
    return super.authorizationFailed()
  }

  public override fun registrationLock(): Boolean {
    return super.registrationLock()
  }

  public override fun rateLimit(): Boolean {
    return super.rateLimit()
  }

  public override fun getError(): Throwable? {
    return super.getError()
  }

  fun getLockedException(): LockedException {
    return error as LockedException
  }

  abstract fun isKbsLocked(): Boolean

  override fun isServerSentError(): Boolean {
    return error is NonSuccessfulResponseCodeException
  }
}


/**
 * Verify processor specific to verifying without needing to handle registration lock.
 */
class VerifyAccountResponseWithoutKbs(response: ServiceResponse<VerifyAccountResponse>) : VerifyAccountResponseProcessor(response) {
  override fun isKbsLocked(): Boolean {
    return false
  }
}