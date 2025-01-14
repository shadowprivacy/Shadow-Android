package su.sres.securesms.stickers;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;

import su.sres.securesms.components.emoji.EmojiUtil;
import su.sres.securesms.database.AttachmentDatabase;
import su.sres.securesms.database.ShadowDatabase;
import su.sres.securesms.database.StickerDatabase;
import su.sres.securesms.database.StickerDatabase.StickerRecordReader;
import su.sres.securesms.database.model.StickerRecord;
import su.sres.core.util.concurrent.SignalExecutors;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class StickerSearchRepository {

  private final StickerDatabase    stickerDatabase;
  private final AttachmentDatabase attachmentDatabase;

  public StickerSearchRepository(@NonNull Context context) {
    this.stickerDatabase    = ShadowDatabase.stickers();
    this.attachmentDatabase = ShadowDatabase.attachments();
  }

  public void searchByEmoji(@NonNull String emoji, @NonNull Callback<List<StickerRecord>> callback) {
    SignalExecutors.BOUNDED.execute(() -> {
      String              searchEmoji = EmojiUtil.getCanonicalRepresentation(emoji);
      List<StickerRecord> out         = new ArrayList<>();
      Set<String>         possible    = EmojiUtil.getAllRepresentations(searchEmoji);

      for (String candidate : possible) {
        try (StickerRecordReader reader = new StickerRecordReader(stickerDatabase.getStickersByEmoji(candidate))) {
          StickerRecord record = null;
          while ((record = reader.getNext()) != null) {
            out.add(record);
          }
        }
      }
      callback.onResult(out);
    });
  }

  public void getStickerFeatureAvailability(@NonNull Callback<Boolean> callback) {
    SignalExecutors.BOUNDED.execute(() -> {
      try (Cursor cursor = stickerDatabase.getAllStickerPacks("1")) {
        if (cursor != null && cursor.moveToFirst()) {
          callback.onResult(true);
        } else {
          callback.onResult(attachmentDatabase.hasStickerAttachments());
        }
      }
    });
  }

  public interface Callback<T> {
    void onResult(T result);
  }
}