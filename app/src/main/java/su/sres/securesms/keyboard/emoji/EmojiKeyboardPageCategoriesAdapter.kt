package su.sres.securesms.keyboard.emoji

import su.sres.securesms.R
import su.sres.securesms.keyboard.KeyboardPageCategoryIconViewHolder
import su.sres.securesms.util.MappingAdapter

class EmojiKeyboardPageCategoriesAdapter(private val onPageSelected: (String) -> Unit) : MappingAdapter() {
  init {
    registerFactory(EmojiKeyboardPageCategoryMappingModel.RecentsMappingModel::class.java, LayoutFactory({ v -> KeyboardPageCategoryIconViewHolder<EmojiKeyboardPageCategoryMappingModel.RecentsMappingModel>(v, onPageSelected) }, R.layout.keyboard_pager_category_icon))
    registerFactory(EmojiKeyboardPageCategoryMappingModel.EmojiCategoryMappingModel::class.java, LayoutFactory({ v -> KeyboardPageCategoryIconViewHolder<EmojiKeyboardPageCategoryMappingModel.EmojiCategoryMappingModel>(v, onPageSelected) }, R.layout.keyboard_pager_category_icon))
  }
}