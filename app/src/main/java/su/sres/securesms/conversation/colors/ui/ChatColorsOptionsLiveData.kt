package su.sres.securesms.conversation.colors.ui

import androidx.lifecycle.LiveData
import su.sres.core.util.concurrent.SignalExecutors
import su.sres.securesms.conversation.colors.ChatColors
import su.sres.securesms.conversation.colors.ChatColorsPalette
import su.sres.securesms.database.ChatColorsDatabase
import su.sres.securesms.database.DatabaseObserver
import su.sres.securesms.database.ShadowDatabase
import su.sres.securesms.dependencies.ApplicationDependencies
import su.sres.securesms.util.concurrent.SerialMonoLifoExecutor
import java.util.concurrent.Executor

class ChatColorsOptionsLiveData : LiveData<List<ChatColors>>() {
  private val chatColorsDatabase: ChatColorsDatabase = ShadowDatabase.chatColors
  private val observer: DatabaseObserver.Observer = DatabaseObserver.Observer { refreshChatColors() }
  private val executor: Executor = SerialMonoLifoExecutor(SignalExecutors.BOUNDED)

  override fun onActive() {
    refreshChatColors()
    ApplicationDependencies.getDatabaseObserver().registerChatColorsObserver(observer)
  }

  override fun onInactive() {
    ApplicationDependencies.getDatabaseObserver().unregisterObserver(observer)
  }

  private fun refreshChatColors() {
    executor.execute {
      val options = mutableListOf<ChatColors>().apply {
        addAll(ChatColorsPalette.Bubbles.all)
        addAll(chatColorsDatabase.getSavedChatColors())
      }

      postValue(options)
    }
  }
}