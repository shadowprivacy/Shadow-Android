package su.sres.securesms.stickers;

import androidx.annotation.NonNull;

import su.sres.securesms.database.model.StickerRecord;

public interface StickerEventListener {
  void onStickerSelected(@NonNull StickerRecord sticker);

  void onStickerManagementClicked();
}
