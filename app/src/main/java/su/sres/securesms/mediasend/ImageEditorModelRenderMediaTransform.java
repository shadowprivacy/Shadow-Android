package su.sres.securesms.mediasend;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import su.sres.core.util.StreamUtil;
import su.sres.securesms.imageeditor.model.EditorModel;
import su.sres.core.util.logging.Log;
import su.sres.securesms.providers.BlobProvider;
import su.sres.securesms.util.MediaUtil;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class ImageEditorModelRenderMediaTransform implements MediaTransform {

    private static final String TAG = Log.tag(ImageEditorModelRenderMediaTransform.class);

    @NonNull  private final EditorModel modelToRender;
    @Nullable private final Point       size;

    ImageEditorModelRenderMediaTransform(@NonNull EditorModel modelToRender) {
        this(modelToRender, null);
    }

    ImageEditorModelRenderMediaTransform(@NonNull EditorModel modelToRender, @Nullable Point size) {
        this.modelToRender = modelToRender;
        this.size          = size;
    }

    @WorkerThread
    @Override
    public @NonNull Media transform(@NonNull Context context, @NonNull Media media) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        Bitmap bitmap = modelToRender.render(context, size);
        try {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream);

            Uri uri = BlobProvider.getInstance()
                    .forData(outputStream.toByteArray())
                    .withMimeType(MediaUtil.IMAGE_JPEG)
                    .createForSingleSessionOnDisk(context);

            return new Media(uri, MediaUtil.IMAGE_JPEG, media.getDate(), bitmap.getWidth(), bitmap.getHeight(), outputStream.size(), 0, false, false, media.getBucketId(), media.getCaption(), Optional.absent());
        } catch (IOException e) {
            Log.w(TAG, "Failed to render image. Using base image.");
            return media;
        } finally {
            bitmap.recycle();
            StreamUtil.close(outputStream);
        }
    }
}