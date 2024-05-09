package su.sres.securesms.mediasend.v2.review

import android.view.View
import android.widget.ImageView
import com.bumptech.glide.Glide
import su.sres.securesms.R
import su.sres.securesms.mediasend.Media
import su.sres.securesms.mms.DecryptableStreamUriLoader
import su.sres.securesms.util.MappingAdapter
import su.sres.securesms.util.MappingModel
import su.sres.securesms.util.MappingViewHolder
import su.sres.securesms.util.MediaUtil
import su.sres.securesms.util.visible

typealias OnSelectedMediaClicked = (Media, Boolean) -> Unit

object MediaReviewSelectedItem {
  fun register(mappingAdapter: MappingAdapter, onSelectedMediaClicked: OnSelectedMediaClicked) {
    mappingAdapter.registerFactory(Model::class.java, MappingAdapter.LayoutFactory({ ViewHolder(it, onSelectedMediaClicked) }, R.layout.v2_media_review_selected_item))
  }

  class Model(val media: Media, val isSelected: Boolean) : MappingModel<Model> {
    override fun areItemsTheSame(newItem: Model): Boolean {
      return media == newItem.media
    }

    override fun areContentsTheSame(newItem: Model): Boolean {
      return media == newItem.media && isSelected == newItem.isSelected
    }
  }

  class ViewHolder(itemView: View, private val onSelectedMediaClicked: OnSelectedMediaClicked) : MappingViewHolder<Model>(itemView) {

    private val imageView: ImageView = itemView.findViewById(R.id.media_review_selected_image)
    private val playOverlay: ImageView = itemView.findViewById(R.id.media_review_play_overlay)
    private val selectedOverlay: ImageView = itemView.findViewById(R.id.media_review_selected_overlay)

    override fun bind(model: Model) {
      Glide.with(imageView)
        .load(DecryptableStreamUriLoader.DecryptableUri(model.media.uri))
        .centerCrop()
        .into(imageView)

      playOverlay.visible = MediaUtil.isNonGifVideo(model.media) && !model.isSelected
      selectedOverlay.isSelected = model.isSelected

      itemView.contentDescription = if (model.isSelected) {
        context.getString(R.string.MediaReviewSelectedItem__tap_to_remove)
      } else {
        context.getString(R.string.MediaReviewSelectedItem__tap_to_select)
      }

      itemView.setOnClickListener { onSelectedMediaClicked(model.media, model.isSelected) }
    }
  }
}