package su.sres.securesms.wallpaper;

import android.util.DisplayMetrics;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;

import su.sres.securesms.R;
import su.sres.securesms.util.DisplayMetricsUtil;
import su.sres.securesms.util.MappingAdapter;
import su.sres.securesms.util.MappingViewHolder;

class ChatWallpaperViewHolder extends MappingViewHolder<ChatWallpaperSelectionMappingModel> {

  private final AspectRatioFrameLayout frame;
  private final ImageView              preview;
  private final EventListener          eventListener;

  public ChatWallpaperViewHolder(@NonNull View itemView, @Nullable EventListener eventListener, @Nullable DisplayMetrics windowDisplayMetrics) {
    super(itemView);
    this.frame         = itemView.findViewById(R.id.chat_wallpaper_preview_frame);
    this.preview       = itemView.findViewById(R.id.chat_wallpaper_preview);
    this.eventListener = eventListener;

    if (windowDisplayMetrics != null) {
      DisplayMetricsUtil.forceAspectRatioToScreenByAdjustingHeight(windowDisplayMetrics, itemView);
    } else if (frame != null) {
      frame.setAspectRatio(1.0f);
    }
  }

  @Override
  public void bind(@NonNull ChatWallpaperSelectionMappingModel model) {
    model.loadInto(preview);

    preview.setColorFilter(ChatWallpaperDimLevelUtil.getDimColorFilterForNightMode(context, model.getWallpaper()));

    if (eventListener != null) {
      preview.setOnClickListener(unused -> {
        if (getAdapterPosition() != RecyclerView.NO_POSITION) {
          eventListener.onModelClick(model);
        }
      });
    }
  }

  public static @NonNull MappingAdapter.Factory<ChatWallpaperSelectionMappingModel> createFactory(@LayoutRes int layout, @Nullable EventListener listener, @Nullable DisplayMetrics windowDisplayMetrics) {
    return new MappingAdapter.LayoutFactory<>(view -> new ChatWallpaperViewHolder(view, listener, windowDisplayMetrics), layout);
  }

  public interface EventListener {
    default void onModelClick(@NonNull ChatWallpaperSelectionMappingModel model) {
      onClick(model.getWallpaper());
    }

    void onClick(@NonNull ChatWallpaper chatWallpaper);
  }
}