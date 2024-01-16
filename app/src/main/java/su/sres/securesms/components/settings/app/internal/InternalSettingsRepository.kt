package su.sres.securesms.components.settings.app.internal

import android.content.Context
import su.sres.core.util.concurrent.SignalExecutors
import su.sres.securesms.emoji.EmojiFiles

class InternalSettingsRepository(context: Context) {

  private val context = context.applicationContext

  fun getEmojiVersionInfo(consumer: (EmojiFiles.Version?) -> Unit) {
    SignalExecutors.BOUNDED.execute {
      consumer(EmojiFiles.Version.readVersion(context))
    }
  }
}