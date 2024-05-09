package su.sres.securesms.keyboard.emoji

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE
import com.google.android.material.appbar.AppBarLayout
import su.sres.securesms.R
import su.sres.securesms.components.emoji.EmojiEventListener
import su.sres.securesms.components.emoji.EmojiPageView
import su.sres.securesms.components.emoji.EmojiPageViewGridAdapter
import su.sres.securesms.components.emoji.EmojiPageViewGridAdapter.EmojiHeader
import su.sres.securesms.keyboard.findListener
import su.sres.securesms.keyvalue.SignalStore
import su.sres.securesms.util.MappingModel
import java.util.Optional

private val DELETE_KEY_EVENT: KeyEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL)

class EmojiKeyboardPageFragment : Fragment(R.layout.keyboard_pager_emoji_page_fragment), EmojiEventListener, EmojiPageViewGridAdapter.VariationSelectorListener {

  private lateinit var viewModel: EmojiKeyboardPageViewModel
  private lateinit var emojiPageView: EmojiPageView
  private lateinit var searchView: View
  private lateinit var emojiCategoriesRecycler: RecyclerView
  private lateinit var backspaceView: View
  private lateinit var eventListener: EmojiEventListener
  private lateinit var callback: Callback
  private lateinit var categoriesAdapter: EmojiKeyboardPageCategoriesAdapter
  private lateinit var searchBar: KeyboardPageSearchView
  private lateinit var appBarLayout: AppBarLayout

  private val categoryUpdateOnScroll = UpdateCategorySelectionOnScroll()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    callback = requireNotNull(findListener())
    emojiPageView = view.findViewById(R.id.emoji_page_view)
    emojiPageView.initialize(this, this, true)
    emojiPageView.addOnScrollListener(categoryUpdateOnScroll)

    searchView = view.findViewById(R.id.emoji_search)
    searchBar = view.findViewById(R.id.emoji_keyboard_search_text)
    emojiCategoriesRecycler = view.findViewById(R.id.emoji_categories_recycler)
    backspaceView = view.findViewById(R.id.emoji_backspace)
    appBarLayout = view.findViewById(R.id.emoji_keyboard_search_appbar)
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)

    viewModel = ViewModelProviders.of(requireActivity(), EmojiKeyboardPageViewModel.Factory(requireContext()))
      .get(EmojiKeyboardPageViewModel::class.java)

    categoriesAdapter = EmojiKeyboardPageCategoriesAdapter { key ->
      scrollTo(key)
      viewModel.onKeySelected(key)
    }

    emojiCategoriesRecycler.adapter = categoriesAdapter

    searchBar.callbacks = EmojiKeyboardPageSearchViewCallbacks()

    searchView.setOnClickListener {
      callback.openEmojiSearch()
    }

    backspaceView.setOnClickListener { eventListener.onKeyEvent(DELETE_KEY_EVENT) }

    viewModel.categories.observe(viewLifecycleOwner) { categories ->
      categoriesAdapter.submitList(categories) {
        (emojiCategoriesRecycler.parent as View).invalidate()
        emojiCategoriesRecycler.parent.requestLayout()
      }
    }

    viewModel.pages.observe(viewLifecycleOwner) { pages ->
      emojiPageView.setList(pages) { (emojiPageView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(1, 0) }
    }

    viewModel.selectedKey.observe(viewLifecycleOwner) { updateCategoryTab(it) }

    eventListener = findListener() ?: throw AssertionError("No emoji listener found")
  }

  private fun updateCategoryTab(key: String) {
    emojiCategoriesRecycler.post {
      val index: Int = categoriesAdapter.indexOfFirst(EmojiKeyboardPageCategoryMappingModel::class.java) { it.key == key }

      if (index != -1) {
        emojiCategoriesRecycler.smoothScrollToPosition(index)
      }
    }
  }

  private fun scrollTo(key: String) {
    emojiPageView.adapter?.let { adapter ->
      val index = adapter.indexOfFirst(EmojiHeader::class.java) { it.key == key }
      if (index != -1) {
        appBarLayout.setExpanded(false, true)
        categoryUpdateOnScroll.startAutoScrolling()
        emojiPageView.smoothScrollToPositionTop(index)
      }
    }
  }

  override fun onEmojiSelected(emoji: String) {
    SignalStore.emojiValues().setPreferredVariation(emoji)
    eventListener.onEmojiSelected(emoji)
    viewModel.addToRecents(emoji)
  }

  override fun onKeyEvent(keyEvent: KeyEvent?) {
    eventListener.onKeyEvent(keyEvent)
  }

  override fun onVariationSelectorStateChanged(open: Boolean) = Unit

  private inner class EmojiKeyboardPageSearchViewCallbacks : KeyboardPageSearchView.Callbacks {
    override fun onClicked() {
      callback.openEmojiSearch()
    }
  }

  private inner class UpdateCategorySelectionOnScroll : RecyclerView.OnScrollListener() {

    private var doneScrolling: Boolean = true

    fun startAutoScrolling() {
      doneScrolling = false
    }

    @Override
    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
      if (newState == SCROLL_STATE_IDLE && !doneScrolling) {
        doneScrolling = true
        onScrolled(recyclerView, 0, 0)
      }
    }

    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
      if (recyclerView.layoutManager == null || !doneScrolling) {
        return
      }

      emojiPageView.adapter?.let { adapter ->
        val layoutManager = recyclerView.layoutManager as LinearLayoutManager
        val index = layoutManager.findFirstCompletelyVisibleItemPosition()
        val item: Optional<MappingModel<*>> = adapter.getModel(index)
        if (item.isPresent && item.get() is EmojiPageViewGridAdapter.HasKey) {
          viewModel.onKeySelected((item.get() as EmojiPageViewGridAdapter.HasKey).key)
        }
      }
    }
  }

  interface Callback {
    fun openEmojiSearch()
  }
}