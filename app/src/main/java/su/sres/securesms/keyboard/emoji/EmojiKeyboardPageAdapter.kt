package su.sres.securesms.keyboard.emoji

import android.view.ViewGroup
import su.sres.securesms.components.emoji.EmojiKeyboardProvider
import su.sres.securesms.components.emoji.EmojiPageView
import su.sres.securesms.components.emoji.EmojiPageViewGridAdapter
import su.sres.securesms.util.MappingAdapter
import su.sres.securesms.util.MappingViewHolder

class EmojiKeyboardPageAdapter(
  private val emojiSelectionListener: EmojiKeyboardProvider.EmojiEventListener,
  private val variationSelectorListener: EmojiPageViewGridAdapter.VariationSelectorListener
) : MappingAdapter() {

  init {
    registerFactory(EmojiPageMappingModel::class.java) { parent ->
      val pageView = EmojiPageView(parent.context, emojiSelectionListener, variationSelectorListener, true)

      val layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
      pageView.layoutParams = layoutParams
      pageView.presentForEmojiKeyboard()

      ViewHolder(pageView)
    }
  }

  private class ViewHolder(
    private val emojiPageView: EmojiPageView,
  ) : MappingViewHolder<EmojiPageMappingModel>(emojiPageView) {

    override fun bind(model: EmojiPageMappingModel) {
      emojiPageView.bindSearchableAdapter(model.emojiPageModel)
    }
  }
}