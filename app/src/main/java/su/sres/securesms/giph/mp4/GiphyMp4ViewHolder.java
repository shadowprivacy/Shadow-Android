package su.sres.securesms.giph.mp4;

import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;

import su.sres.securesms.R;
import su.sres.securesms.conversation.colors.ChatColorsPalette;
import su.sres.securesms.giph.model.ChunkedImageUrl;
import su.sres.securesms.giph.model.GiphyImage;
import su.sres.securesms.mms.GlideApp;
import su.sres.securesms.util.Projection;
import su.sres.securesms.util.Util;
import su.sres.securesms.util.ViewUtil;

/**
 * Holds a view which will either play back an MP4 gif or show its still.
 */
final class GiphyMp4ViewHolder extends RecyclerView.ViewHolder implements GiphyMp4Playable {

  private static final Projection.Corners CORNERS = new Projection.Corners(ViewUtil.dpToPx(8));

  private final AspectRatioFrameLayout   container;
  private final ImageView                stillImage;
  private final GiphyMp4Adapter.Callback listener;
  private final Drawable                 placeholder;

  private float     aspectRatio;
  private MediaItem mediaItem;

  GiphyMp4ViewHolder(@NonNull View itemView,
                     @Nullable GiphyMp4Adapter.Callback listener)
  {
    super(itemView);
    this.container   = itemView.findViewById(R.id.container);
    this.listener    = listener;
    this.stillImage  = itemView.findViewById(R.id.still_image);
    this.placeholder = new ColorDrawable(Util.getRandomElement(ChatColorsPalette.Names.getAll()).getColor(itemView.getContext()));

    container.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH);
  }

  void onBind(@NonNull GiphyImage giphyImage) {
    aspectRatio = giphyImage.getGifAspectRatio();
    mediaItem   = MediaItem.fromUri(Uri.parse(giphyImage.getMp4PreviewUrl()));

    container.setAspectRatio(aspectRatio);

    loadPlaceholderImage(giphyImage);

    itemView.setOnClickListener(v -> listener.onClick(giphyImage));
  }

  @Override
  public void showProjectionArea() {
    container.setAlpha(1f);
  }

  @Override
  public void hideProjectionArea() {
    container.setAlpha(0f);
  }

  @Override
  public @NonNull MediaItem getMediaItem() {
    return mediaItem;
  }

  @Override
  public @NonNull Projection getGiphyMp4PlayableProjection(@NonNull ViewGroup recyclerView) {
    return Projection.relativeToParent(recyclerView, container, CORNERS);
  }

  @Override
  public boolean canPlayContent() {
    return true;
  }

  private void loadPlaceholderImage(@NonNull GiphyImage giphyImage) {
    GlideApp.with(itemView)
            .load(new ChunkedImageUrl(giphyImage.getStillUrl()))
            .placeholder(placeholder)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(stillImage);
  }
}
