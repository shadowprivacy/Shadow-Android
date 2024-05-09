package su.sres.securesms.registration

import su.sres.signalservice.api.push.exceptions.LocalRateLimitException
import su.sres.signalservice.internal.ServiceResponse
import su.sres.signalservice.internal.ServiceResponseProcessor
import su.sres.signalservice.internal.push.RequestVerificationCodeResponse

/**
 * Process responses from requesting an SMS or Phone code from the server.
 */
class RequestVerificationCodeResponseProcessor(response: ServiceResponse<RequestVerificationCodeResponse>) : ServiceResponseProcessor<RequestVerificationCodeResponse>(response) {
  public override fun captchaRequired(): Boolean {
    return super.captchaRequired()
  }

  public override fun rateLimit(): Boolean {
    return super.rateLimit()
  }

  public override fun getError(): Throwable? {
    return super.getError()
  }

  fun localRateLimit(): Boolean {
    return error is LocalRateLimitException
  }

  companion object {
    @JvmStatic
    fun forLocalRateLimit(): RequestVerificationCodeResponseProcessor {
      val response: ServiceResponse<RequestVerificationCodeResponse> = ServiceResponse.forExecutionError(LocalRateLimitException())
      return RequestVerificationCodeResponseProcessor(response)
    }
  }
}