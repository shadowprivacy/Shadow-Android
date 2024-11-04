package su.sres.securesms.util

import com.google.android.gms.wallet.WalletConstants
import su.sres.donations.GooglePayApi
import su.sres.donations.StripeApi
import su.sres.securesms.BuildConfig

object Environment {
  const val IS_STAGING: Boolean = BuildConfig.BUILD_ENVIRONMENT_TYPE == "Staging"

  object Donations {
    val GOOGLE_PAY_CONFIGURATION = GooglePayApi.Configuration(
      walletEnvironment = if (IS_STAGING) WalletConstants.ENVIRONMENT_TEST else WalletConstants.ENVIRONMENT_PRODUCTION
    )

    val STRIPE_CONFIGURATION = StripeApi.Configuration(
      publishableKey = BuildConfig.STRIPE_PUBLISHABLE_KEY
    )
  }
}