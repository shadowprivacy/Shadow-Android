package su.sres.securesms.wallpaper.crop;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import su.sres.core.util.logging.Log;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.wallpaper.ChatWallpaper;
import su.sres.securesms.wallpaper.WallpaperStorage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

final class WallpaperCropRepository {

    private static final String TAG = Log.tag(WallpaperCropRepository.class);

    @Nullable private final RecipientId recipientId;
    private final           Context     context;

    public WallpaperCropRepository(@Nullable RecipientId recipientId) {
        this.context     = ApplicationDependencies.getApplication();
        this.recipientId = recipientId;
    }

    @WorkerThread
    @NonNull ChatWallpaper setWallPaper(byte[] bytes) throws IOException {
        try (InputStream inputStream = new ByteArrayInputStream(bytes)) {
            ChatWallpaper wallpaper = WallpaperStorage.save(context, inputStream, "webp");

            if (recipientId != null) {
                Log.i(TAG, "Setting image wallpaper for " + recipientId);
                DatabaseFactory.getRecipientDatabase(context).setWallpaper(recipientId, wallpaper);
            } else {
                Log.i(TAG, "Setting image wallpaper for default");
                SignalStore.wallpaper().setWallpaper(context, wallpaper);
            }

            return wallpaper;
        }
    }
}
