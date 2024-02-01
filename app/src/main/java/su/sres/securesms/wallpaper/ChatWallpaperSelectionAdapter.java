package su.sres.securesms.wallpaper;

import androidx.annotation.Nullable;

import su.sres.securesms.R;
import su.sres.securesms.util.MappingAdapter;

class ChatWallpaperSelectionAdapter extends MappingAdapter {
    ChatWallpaperSelectionAdapter(@Nullable ChatWallpaperViewHolder.EventListener eventListener) {
        registerFactory(ChatWallpaperSelectionMappingModel.class, ChatWallpaperViewHolder.createFactory(R.layout.chat_wallpaper_selection_fragment_adapter_item, eventListener, null));
    }
}
