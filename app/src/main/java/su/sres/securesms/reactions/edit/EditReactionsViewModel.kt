package su.sres.securesms.reactions.edit

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import su.sres.core.util.concurrent.SignalExecutors
import su.sres.securesms.database.ShadowDatabase
import su.sres.securesms.dependencies.ApplicationDependencies
import su.sres.securesms.keyvalue.EmojiValues
import su.sres.securesms.keyvalue.SignalStore
import su.sres.securesms.recipients.Recipient
import su.sres.securesms.util.livedata.LiveDataUtil
import su.sres.securesms.util.livedata.Store

class EditReactionsViewModel : ViewModel() {

  private val emojiValues: EmojiValues = SignalStore.emojiValues()
  private val store: Store<State> = Store(State(reactions = emojiValues.reactions.map { emojiValues.getPreferredVariation(it) }))

  val reactions: LiveData<List<String>> = LiveDataUtil.mapDistinct(store.stateLiveData, State::reactions)
  val selection: LiveData<Int> = LiveDataUtil.mapDistinct(store.stateLiveData, State::selection)

  fun setSelection(selection: Int) {
    store.update { it.copy(selection = selection) }
  }

  fun onEmojiSelected(emoji: String) {
    store.update { state ->
      if (state.selection != NO_SELECTION && state.selection in state.reactions.indices) {
        val preferredEmoji: String = emojiValues.getPreferredVariation(emoji)
        val newReactions: List<String> = state.reactions.toMutableList().apply { set(state.selection, preferredEmoji) }
        state.copy(reactions = newReactions)
      } else {
        state
      }
    }
  }

  fun resetToDefaults() {
    store.update { it.copy(reactions = EmojiValues.DEFAULT_REACTIONS_LIST) }
  }

  fun save() {
    emojiValues.reactions = store.state.reactions
    SignalExecutors.BOUNDED.execute {
      ShadowDatabase.recipients.markNeedsSync(Recipient.self().id)
      // StorageSyncHelper.scheduleSyncForDataChange()
    }
  }

  companion object {
    const val NO_SELECTION: Int = -1
  }

  data class State(val selection: Int = NO_SELECTION, val reactions: List<String>)
}