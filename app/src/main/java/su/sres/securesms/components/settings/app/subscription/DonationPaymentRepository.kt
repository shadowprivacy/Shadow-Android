package su.sres.securesms.components.settings.app.subscription

import android.app.Activity
import android.content.Intent
import com.google.android.gms.wallet.PaymentData
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import su.sres.core.util.money.FiatMoney
import su.sres.donations.GooglePayApi
import su.sres.donations.GooglePayPaymentSource
import su.sres.donations.StripeApi
import su.sres.securesms.BuildConfig
import su.sres.securesms.dependencies.ApplicationDependencies

class DonationPaymentRepository(activity: Activity) : StripeApi.PaymentIntentFetcher {

  private val configuration = StripeApi.Configuration(publishableKey = BuildConfig.STRIPE_PUBLISHABLE_KEY)
  private val googlePayApi = GooglePayApi(activity, StripeApi.Gateway(configuration))
  private val stripeApi = StripeApi(configuration, this, ApplicationDependencies.getOkHttpClient())

  fun isGooglePayAvailable(): Completable = googlePayApi.queryIsReadyToPay()

  fun requestTokenFromGooglePay(price: FiatMoney, label: String, requestCode: Int) {
    googlePayApi.requestPayment(price, label, requestCode)
  }

  fun onActivityResult(
    requestCode: Int,
    resultCode: Int,
    data: Intent?,
    expectedRequestCode: Int,
    paymentsRequestCallback: GooglePayApi.PaymentRequestCallback
  ) {
    googlePayApi.onActivityResult(requestCode, resultCode, data, expectedRequestCode, paymentsRequestCallback)
  }

  fun continuePayment(price: FiatMoney, paymentData: PaymentData): Completable {
    return stripeApi.createPaymentIntent(price)
      .flatMapCompletable { result ->
        when (result) {
          is StripeApi.CreatePaymentIntentResult.AmountIsTooSmall -> Completable.error(Exception("Amount is too small"))
          is StripeApi.CreatePaymentIntentResult.AmountIsTooLarge -> Completable.error(Exception("Amount is too large"))
          is StripeApi.CreatePaymentIntentResult.CurrencyIsNotSupported -> Completable.error(Exception("Currency is not supported"))
          is StripeApi.CreatePaymentIntentResult.Success -> stripeApi.confirmPaymentIntent(GooglePayPaymentSource(paymentData), result.paymentIntent)
        }
      }
  }

  override fun fetchPaymentIntent(price: FiatMoney, description: String?): Single<StripeApi.PaymentIntent> {
    return ApplicationDependencies
      .getDonationsService()
      .createDonationIntentWithAmount(price.minimumUnitPrecisionString, price.currency.currencyCode)
      .map { StripeApi.PaymentIntent(it.result.get().id, it.result.get().clientSecret) }
  }
}