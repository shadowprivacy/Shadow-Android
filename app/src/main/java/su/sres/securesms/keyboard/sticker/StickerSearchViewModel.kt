package su.sres.securesms.keyboard.sticker

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import su.sres.securesms.database.model.StickerRecord
import su.sres.securesms.util.livedata.LiveDataUtil

class StickerSearchViewModel(private val searchRepository: StickerSearchRepository) : ViewModel() {

  private val searchQuery: MutableLiveData<String> = MutableLiveData("")

  val searchResults: LiveData<List<KeyboardStickerListAdapter.Sticker>> = LiveDataUtil.mapAsync(searchQuery) { q ->
    searchRepository.search(q)
      .map { KeyboardStickerListAdapter.Sticker(it.packId, it) }
  }

  fun query(query: String) {
    searchQuery.postValue(query)
  }

  class Factory(context: Context) : ViewModelProvider.Factory {
    val repository = StickerSearchRepository(context)

    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
      return requireNotNull(modelClass.cast(StickerSearchViewModel(repository)))
    }
  }
}