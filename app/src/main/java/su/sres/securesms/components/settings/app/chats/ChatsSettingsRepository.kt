package su.sres.securesms.components.settings.app.chats

import android.content.Context
import su.sres.core.util.concurrent.SignalExecutors
import su.sres.securesms.database.DatabaseFactory
import su.sres.securesms.dependencies.ApplicationDependencies
import su.sres.securesms.jobs.MultiDeviceConfigurationUpdateJob
import su.sres.securesms.keyvalue.SignalStore
import su.sres.securesms.megaphone.Megaphones
import su.sres.securesms.recipients.Recipient
// import su.sres.securesms.storage.StorageSyncHelper
import su.sres.securesms.util.TextSecurePreferences

class ChatsSettingsRepository {

  private val context: Context = ApplicationDependencies.getApplication()

  fun syncLinkPreviewsState() {
    SignalExecutors.BOUNDED.execute {
      val isLinkPreviewsEnabled = SignalStore.settings().isLinkPreviewsEnabled

      DatabaseFactory.getRecipientDatabase(context).markNeedsSync(Recipient.self().id)
      // StorageSyncHelper.scheduleSyncForDataChange()
      ApplicationDependencies.getJobManager().add(
        MultiDeviceConfigurationUpdateJob(
          TextSecurePreferences.isReadReceiptsEnabled(context),
          TextSecurePreferences.isTypingIndicatorsEnabled(context),
          TextSecurePreferences.isShowUnidentifiedDeliveryIndicatorsEnabled(context),
          isLinkPreviewsEnabled
        )
      )
      if (isLinkPreviewsEnabled) {
        ApplicationDependencies.getMegaphoneRepository().markFinished(Megaphones.Event.LINK_PREVIEWS)
      }
    }
  }
}