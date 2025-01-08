package su.sres.securesms.components.settings.app.privacy

import android.content.Context
import su.sres.core.util.concurrent.SignalExecutors
import su.sres.securesms.database.ShadowDatabase
import su.sres.securesms.dependencies.ApplicationDependencies
import su.sres.securesms.jobs.MultiDeviceConfigurationUpdateJob
import su.sres.securesms.keyvalue.SignalStore
import su.sres.securesms.recipients.Recipient
import su.sres.securesms.util.TextSecurePreferences

class PrivacySettingsRepository {

  private val context: Context = ApplicationDependencies.getApplication()

  fun getBlockedCount(consumer: (Int) -> Unit) {
    SignalExecutors.BOUNDED.execute {
      val recipientDatabase = ShadowDatabase.recipients

      consumer(recipientDatabase.blocked.count)
    }
  }

  fun syncReadReceiptState() {
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

  fun syncTypingIndicatorsState() {
    val enabled = TextSecurePreferences.isTypingIndicatorsEnabled(context)

    ShadowDatabase.recipients.markNeedsSync(Recipient.self().id)
    // StorageSyncHelper.scheduleSyncForDataChange()
    ApplicationDependencies.getJobManager().add(
      MultiDeviceConfigurationUpdateJob(
        TextSecurePreferences.isReadReceiptsEnabled(context),
        enabled,
        TextSecurePreferences.isShowUnidentifiedDeliveryIndicatorsEnabled(context),
        SignalStore.settings().isLinkPreviewsEnabled
      )
    )

    if (!enabled) {
      ApplicationDependencies.getTypingStatusRepository().clear()
    }
  }
}