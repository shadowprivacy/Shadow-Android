package su.sres.securesms.keyboard.emoji

import su.sres.securesms.components.emoji.EmojiPageModel
import su.sres.securesms.components.emoji.EmojiPageViewGridAdapter
import su.sres.securesms.components.emoji.RecentEmojiPageModel
import su.sres.securesms.components.emoji.parsing.EmojiTree
import su.sres.securesms.emoji.EmojiCategory
import su.sres.securesms.emoji.EmojiSource
import su.sres.securesms.util.MappingModel

fun EmojiPageModel.toMappingModels(): List<MappingModel<*>> {
  val emojiTree: EmojiTree = EmojiSource.latest.emojiTree

  return displayEmoji.map {
    val isTextEmoji = EmojiCategory.EMOTICONS.key == key || (RecentEmojiPageModel.KEY == key && emojiTree.getEmoji(it.value, 0, it.value.length) == null)

    if (isTextEmoji) {
      EmojiPageViewGridAdapter.EmojiTextModel(key, it)
    } else {
      EmojiPageViewGridAdapter.EmojiModel(key, it)
    }
  }
}