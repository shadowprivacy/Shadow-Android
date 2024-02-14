package su.sres.securesms.keyboard.emoji

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.annotation.AttrRes
import su.sres.securesms.R
import su.sres.securesms.components.emoji.RecentEmojiPageModel
import su.sres.securesms.emoji.EmojiCategory
import su.sres.securesms.keyboard.KeyboardPageCategoryIconMappingModel
import su.sres.securesms.util.ThemeUtil

sealed class EmojiKeyboardPageCategoryMappingModel(
  override val key: String,
  @AttrRes val iconId: Int,
  override val selected: Boolean
) : KeyboardPageCategoryIconMappingModel<EmojiKeyboardPageCategoryMappingModel> {

  override fun getIcon(context: Context): Drawable {
    return requireNotNull(ThemeUtil.getThemedDrawable(context, iconId))
  }

  override fun areItemsTheSame(newItem: EmojiKeyboardPageCategoryMappingModel): Boolean {
    return newItem.key == key
  }

  class RecentsMappingModel(selected: Boolean) : EmojiKeyboardPageCategoryMappingModel(RecentEmojiPageModel.KEY, R.attr.emoji_category_recent, selected) {
    override fun areContentsTheSame(newItem: EmojiKeyboardPageCategoryMappingModel): Boolean {
      return newItem is RecentsMappingModel && super.areContentsTheSame(newItem)
    }
  }

  class EmojiCategoryMappingModel(private val emojiCategory: EmojiCategory, selected: Boolean) : EmojiKeyboardPageCategoryMappingModel(emojiCategory.key, emojiCategory.icon, selected) {
    override fun areContentsTheSame(newItem: EmojiKeyboardPageCategoryMappingModel): Boolean {
      return newItem is EmojiCategoryMappingModel &&
        super.areContentsTheSame(newItem) &&
        newItem.emojiCategory == emojiCategory
    }
  }

  override fun areContentsTheSame(newItem: EmojiKeyboardPageCategoryMappingModel): Boolean {
    return areItemsTheSame(newItem) && selected == newItem.selected
  }
}