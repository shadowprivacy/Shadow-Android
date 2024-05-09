package su.sres.securesms.mediasend.v2.gallery

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

typealias OnSelectedMediaClicked = (Media) -> Unit

object MediaGallerySelectedItem {

  fun register(mappingAdapter: MappingAdapter, onSelectedMediaClicked: OnSelectedMediaClicked) {
    mappingAdapter.registerFactory(Model::class.java, MappingAdapter.LayoutFactory({ ViewHolder(it, onSelectedMediaClicked) }, R.layout.v2_media_selection_item))
  }

  class Model(val media: Media) : MappingModel<Model> {
    override fun areItemsTheSame(newItem: Model): Boolean {
      return media.uri == newItem.media.uri
    }

    override fun areContentsTheSame(newItem: Model): Boolean {
      return media.uri == newItem.media.uri
    }
  }

  class ViewHolder(itemView: View, private val onSelectedMediaClicked: OnSelectedMediaClicked) : MappingViewHolder<Model>(itemView) {

    private val imageView: ImageView = itemView.findViewById(R.id.media_selection_image)
    private val videoOverlay: ImageView = itemView.findViewById(R.id.media_selection_play_overlay)

    override fun bind(model: Model) {
      Glide.with(imageView)
        .load(DecryptableStreamUriLoader.DecryptableUri(model.media.uri))
        .centerCrop()
        .into(imageView)

      videoOverlay.visible = MediaUtil.isVideo(model.media.mimeType) && !model.media.isVideoGif
      itemView.setOnClickListener { onSelectedMediaClicked(model.media) }
    }
  }
}