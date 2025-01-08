package su.sres.securesms.components.settings.app.privacy.advanced

import android.content.Context
import com.google.firebase.iid.FirebaseInstanceId
import su.sres.core.util.concurrent.SignalExecutors
import su.sres.core.util.logging.Log
import su.sres.securesms.dependencies.ApplicationDependencies
import su.sres.securesms.jobs.MultiDeviceConfigurationUpdateJob
import su.sres.securesms.keyvalue.SignalStore
import su.sres.securesms.recipients.Recipient
// import su.sres.securesms.storage.StorageSyncHelper
import su.sres.securesms.util.TextSecurePreferences
import org.whispersystems.libsignal.util.guava.Optional
import su.sres.securesms.database.ShadowDatabase
import su.sres.signalservice.api.push.exceptions.AuthorizationFailedException
import java.io.IOException

private val TAG = Log.tag(AdvancedPrivacySettingsRepository::class.java)

class AdvancedPrivacySettingsRepository(private val context: Context) {

  fun disablePushMessages(consumer: (DisablePushMessagesResult) -> Unit) {
    SignalExecutors.BOUNDED.execute {
      val result = try {
        val accountManager = ApplicationDependencies.getSignalServiceAccountManager()
        try {
          accountManager.setGcmId(Optional.absent())
        } catch (e: AuthorizationFailedException) {
          Log.w(TAG, e)
        }
        if (SignalStore.account().fcmEnabled) {
          FirebaseInstanceId.getInstance().deleteInstanceId()
        }
        DisablePushMessagesResult.SUCCESS
      } catch (ioe: IOException) {
        Log.w(TAG, ioe)
        DisablePushMessagesResult.NETWORK_ERROR
      }

      consumer(result)
    }
  }

  fun syncShowSealedSenderIconState() {
    SignalExecutors.BOUNDED.execute {
      ShadowDatabase.recipients.markNeedsSync(Recipient.self().id)
      // StorageSyncHelper.scheduleSyncForDataChange()
      ApplicationDependencies.getJobManager().add(
        MultiDeviceConfigurationUpdateJob(
          TextSecurePreferences.isReadReceiptsEnabled(context),
          TextSecurePreferences.isTypingIndicatorsEnabled(context),
          TextSecurePreferences.isShowUnidentifiedDeliveryIndicatorsEnabled(context),
          SignalStore.settings().isLinkPreviewsEnabled
        )
      )
    }
  }

  enum class DisablePushMessagesResult {
    SUCCESS,
    NETWORK_ERROR
  }
}