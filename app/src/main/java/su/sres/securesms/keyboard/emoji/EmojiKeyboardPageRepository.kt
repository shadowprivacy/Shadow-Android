package su.sres.securesms.keyboard.emoji

import android.content.Context
import su.sres.core.util.concurrent.SignalExecutors
import su.sres.securesms.components.emoji.EmojiKeyboardProvider
import su.sres.securesms.components.emoji.EmojiPageModel
import su.sres.securesms.components.emoji.RecentEmojiPageModel
import su.sres.securesms.emoji.EmojiSource.Companion.latest
import java.util.function.Consumer

class EmojiKeyboardPageRepository(context: Context) {

  private val recentEmojiPageModel: RecentEmojiPageModel = RecentEmojiPageModel(context, EmojiKeyboardProvider.RECENT_STORAGE_KEY)

  fun getEmoji(consumer: Consumer<List<EmojiPageModel>>) {
    SignalExecutors.BOUNDED.execute {
      val list = mutableListOf<EmojiPageModel>()
      list += recentEmojiPageModel
      list += latest.displayPages
      consumer.accept(list)
    }
  }
}