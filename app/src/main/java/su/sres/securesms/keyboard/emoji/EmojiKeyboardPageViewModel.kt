package su.sres.securesms.keyboard.emoji

import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import su.sres.securesms.components.emoji.EmojiKeyboardProvider
import su.sres.securesms.components.emoji.RecentEmojiPageModel
import su.sres.securesms.dependencies.ApplicationDependencies
import su.sres.securesms.emoji.EmojiCategory
import su.sres.securesms.emoji.EmojiSource
import su.sres.securesms.util.DefaultValueLiveData
import su.sres.securesms.util.MappingModelList

class EmojiKeyboardPageViewModel : ViewModel() {

  private val internalSelectedKey = DefaultValueLiveData<String>(getStartingTab())

  val selectedKey: LiveData<String>
    get() = internalSelectedKey

  val categories: LiveData<MappingModelList> = Transformations.map(internalSelectedKey) { selected ->
    MappingModelList().apply {
      add(EmojiKeyboardPageCategoryMappingModel.RecentsMappingModel(selected == EmojiKeyboardPageCategoryMappingModel.RecentsMappingModel.KEY))

      EmojiCategory.values().forEach {
        add(EmojiKeyboardPageCategoryMappingModel.EmojiCategoryMappingModel(it, it.key == selected))
      }
    }
  }

  val pages: LiveData<MappingModelList> = Transformations.map(categories) { categories ->
    MappingModelList().apply {
      categories.forEach {
        add(getPageForCategory(it as EmojiKeyboardPageCategoryMappingModel))
      }
    }
  }

  fun onKeySelected(key: String) {
    internalSelectedKey.value = key
  }

  private fun getPageForCategory(mappingModel: EmojiKeyboardPageCategoryMappingModel): EmojiPageMappingModel {
    val page = if (mappingModel.key == EmojiKeyboardPageCategoryMappingModel.RecentsMappingModel.KEY) {
      RecentEmojiPageModel(ApplicationDependencies.getApplication(), EmojiKeyboardProvider.RECENT_STORAGE_KEY)
    } else {
      EmojiSource.latest.displayPages.first { it.iconAttr == mappingModel.iconId }
    }

    return EmojiPageMappingModel(mappingModel.key, page)
  }

  fun addToRecents(emoji: String) {
    RecentEmojiPageModel(ApplicationDependencies.getApplication(), EmojiKeyboardProvider.RECENT_STORAGE_KEY).onCodePointSelected(emoji)
  }

  companion object {
    fun getStartingTab(): String {
      return if (RecentEmojiPageModel.hasRecents(ApplicationDependencies.getApplication(), EmojiKeyboardProvider.RECENT_STORAGE_KEY)) {
        EmojiKeyboardPageCategoryMappingModel.RecentsMappingModel.KEY
      } else {
        EmojiCategory.PEOPLE.key
      }
    }
  }
}