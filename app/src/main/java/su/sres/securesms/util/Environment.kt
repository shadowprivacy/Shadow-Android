package su.sres.securesms.util

import su.sres.securesms.BuildConfig

object Environment {
  const val IS_STAGING: Boolean = BuildConfig.BUILD_ENVIRONMENT_TYPE == "Staging"
}