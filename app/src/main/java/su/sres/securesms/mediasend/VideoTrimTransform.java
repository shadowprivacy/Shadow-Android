package su.sres.securesms.mediasend;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import su.sres.securesms.database.AttachmentDatabase;
import org.whispersystems.libsignal.util.guava.Optional;

public final class VideoTrimTransform implements MediaTransform {

    private final MediaSendVideoFragment.Data data;

    VideoTrimTransform(@NonNull MediaSendVideoFragment.Data data) {
        this.data = data;
    }

    @WorkerThread
    @Override
    public @NonNull Media transform(@NonNull Context context, @NonNull Media media) {
        return new Media(media.getUri(),
                media.getMimeType(),
                media.getDate(),
                media.getWidth(),
                media.getHeight(),
                media.getSize(),
                media.getDuration(),
                media.isBorderless(),
                media.getBucketId(),
                media.getCaption(),
                Optional.of(new AttachmentDatabase.TransformProperties(false, data.durationEdited, data.startTimeUs, data.endTimeUs)));
    }
}