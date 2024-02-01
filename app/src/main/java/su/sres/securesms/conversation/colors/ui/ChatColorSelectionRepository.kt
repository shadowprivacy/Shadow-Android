package su.sres.securesms.conversation.colors.ui

import android.content.Context
import su.sres.core.util.concurrent.SignalExecutors
import su.sres.securesms.conversation.colors.ChatColors
import su.sres.securesms.conversation.colors.ChatColorsPalette
import su.sres.securesms.database.DatabaseFactory
import su.sres.securesms.keyvalue.SignalStore
import su.sres.securesms.recipients.Recipient
import su.sres.securesms.recipients.RecipientId
import su.sres.securesms.wallpaper.ChatWallpaper

sealed class ChatColorSelectionRepository(context: Context) {

  protected val context: Context = context.applicationContext

  abstract fun getWallpaper(consumer: (ChatWallpaper?) -> Unit)
  abstract fun getChatColors(consumer: (ChatColors) -> Unit)
  abstract fun save(chatColors: ChatColors, onSaved: () -> Unit)

  fun duplicate(chatColors: ChatColors) {
    SignalExecutors.BOUNDED.execute {
      val duplicate = chatColors.withId(ChatColors.Id.NotSet)
      DatabaseFactory.getChatColorsDatabase(context).saveChatColors(duplicate)
    }
  }

  fun getUsageCount(chatColorsId: ChatColors.Id, consumer: (Int) -> Unit) {
    SignalExecutors.BOUNDED.execute {
      consumer(DatabaseFactory.getRecipientDatabase(context).getColorUsageCount(chatColorsId))
    }
  }

  fun delete(chatColors: ChatColors, onDeleted: () -> Unit) {
    SignalExecutors.BOUNDED.execute {
      DatabaseFactory.getChatColorsDatabase(context).deleteChatColors(chatColors)
      onDeleted()
    }
  }

  private class Global(context: Context) : ChatColorSelectionRepository(context) {
    override fun getWallpaper(consumer: (ChatWallpaper?) -> Unit) {
      consumer(SignalStore.wallpaper().wallpaper)
    }

    override fun getChatColors(consumer: (ChatColors) -> Unit) {
      if (SignalStore.chatColorsValues().hasChatColors) {
        consumer(requireNotNull(SignalStore.chatColorsValues().chatColors))
      } else {
        getWallpaper { wallpaper ->
          if (wallpaper != null) {
            consumer(wallpaper.autoChatColors)
          } else {
            consumer(ChatColorsPalette.Bubbles.default.withId(ChatColors.Id.Auto))
          }
        }
      }
    }

    override fun save(chatColors: ChatColors, onSaved: () -> Unit) {
      if (chatColors.id == ChatColors.Id.Auto) {
        SignalStore.chatColorsValues().chatColors = null
      } else {
        SignalStore.chatColorsValues().chatColors = chatColors
      }
      onSaved()
    }
  }

  private class Single(context: Context, private val recipientId: RecipientId) : ChatColorSelectionRepository(context) {
    override fun getWallpaper(consumer: (ChatWallpaper?) -> Unit) {
      SignalExecutors.BOUNDED.execute {
        val recipient = Recipient.resolved(recipientId)
        consumer(recipient.wallpaper)
      }
    }

    override fun getChatColors(consumer: (ChatColors) -> Unit) {
      SignalExecutors.BOUNDED.execute {
        val recipient = Recipient.resolved(recipientId)
        consumer(recipient.chatColors)
      }
    }

    override fun save(chatColors: ChatColors, onSaved: () -> Unit) {
      SignalExecutors.BOUNDED.execute {
        val recipientDatabase = DatabaseFactory.getRecipientDatabase(context)
        recipientDatabase.setColor(recipientId, chatColors)
        onSaved()
      }
    }
  }

  companion object {
    fun create(context: Context, recipientId: RecipientId?): ChatColorSelectionRepository {
      return if (recipientId != null) {
        Single(context, recipientId)
      } else {
        Global(context)
      }
    }
  }
}