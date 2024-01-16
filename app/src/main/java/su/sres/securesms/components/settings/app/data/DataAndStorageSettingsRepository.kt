package su.sres.securesms.components.settings.app.data

import android.content.Context
import su.sres.core.util.concurrent.SignalExecutors
import su.sres.securesms.database.DatabaseFactory
import su.sres.securesms.dependencies.ApplicationDependencies

class DataAndStorageSettingsRepository {

  private val context: Context = ApplicationDependencies.getApplication()

  fun getTotalStorageUse(consumer: (Long) -> Unit) {
    SignalExecutors.BOUNDED.execute {
      val breakdown = DatabaseFactory.getMediaDatabase(context).storageBreakdown

      consumer(listOf(breakdown.audioSize, breakdown.documentSize, breakdown.photoSize, breakdown.videoSize).sum())
    }
  }
}