package su.sres.securesms.conversation.colors.ui.custom

import android.content.Context
import su.sres.core.util.concurrent.SignalExecutors
import su.sres.securesms.conversation.colors.ChatColors
import su.sres.securesms.database.DatabaseFactory
import su.sres.securesms.keyvalue.SignalStore
import su.sres.securesms.recipients.Recipient
import su.sres.securesms.recipients.RecipientId
import su.sres.securesms.wallpaper.ChatWallpaper

class CustomChatColorCreatorRepository(private val context: Context) {
  fun loadColors(chatColorsId: ChatColors.Id, consumer: (ChatColors) -> Unit) {
    SignalExecutors.BOUNDED.execute {
      val chatColorsDatabase = DatabaseFactory.getChatColorsDatabase(context)
      val chatColors = chatColorsDatabase.getById(chatColorsId)

      consumer(chatColors)
    }
  }

  fun getWallpaper(recipientId: RecipientId?, consumer: (ChatWallpaper?) -> Unit) {
    SignalExecutors.BOUNDED.execute {
      if (recipientId != null) {
        val recipient = Recipient.resolved(recipientId)
        consumer(recipient.wallpaper)
      } else {
        consumer(SignalStore.wallpaper().wallpaper)
      }
    }
  }

  fun setChatColors(chatColors: ChatColors, consumer: (ChatColors) -> Unit) {
    SignalExecutors.BOUNDED.execute {
      val chatColorsDatabase = DatabaseFactory.getChatColorsDatabase(context)
      val savedColors = chatColorsDatabase.saveChatColors(chatColors)

      consumer(savedColors)
    }
  }

  fun getUsageCount(chatColorsId: ChatColors.Id, consumer: (Int) -> Unit) {
    SignalExecutors.BOUNDED.execute {
      val recipientsDatabase = DatabaseFactory.getRecipientDatabase(context)

      consumer(recipientsDatabase.getColorUsageCount(chatColorsId))
    }
  }
}