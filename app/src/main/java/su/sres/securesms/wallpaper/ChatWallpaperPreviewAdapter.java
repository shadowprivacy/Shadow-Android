package su.sres.securesms.wallpaper;

import su.sres.securesms.R;
import su.sres.securesms.util.MappingAdapter;

class ChatWallpaperPreviewAdapter extends MappingAdapter {
    ChatWallpaperPreviewAdapter() {
        registerFactory(ChatWallpaperSelectionMappingModel.class, ChatWallpaperViewHolder.createFactory(R.layout.chat_wallpaper_preview_fragment_adapter_item, null, null));
    }
}
