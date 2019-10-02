package su.sres.securesms.jobs;

import android.content.Context;
import android.media.MediaDataSource;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import su.sres.securesms.R;
import su.sres.securesms.attachments.Attachment;
import su.sres.securesms.attachments.DatabaseAttachment;
import su.sres.securesms.database.AttachmentDatabase;
import su.sres.securesms.mms.DecryptableStreamUriLoader;
import su.sres.securesms.mms.MediaConstraints;
import su.sres.securesms.mms.MediaStream;
import su.sres.securesms.mms.MmsException;
import su.sres.securesms.service.GenericForegroundService;
import su.sres.securesms.service.NotificationController;
import su.sres.securesms.transport.UndeliverableMessageException;
import su.sres.securesms.util.BitmapDecodingException;
import su.sres.securesms.util.BitmapUtil;
import su.sres.securesms.util.MediaUtil;
import su.sres.securesms.video.InMemoryTranscoder;
import su.sres.securesms.video.videoconverter.BadVideoException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class MediaResizer {

    @NonNull private final Context          context;
    @NonNull private final MediaConstraints constraints;

    MediaResizer(@NonNull Context context,
                 @NonNull MediaConstraints constraints)
    {
        this.context     = context;
        this.constraints = constraints;
    }

    List<Attachment> scaleAndStripExifToDatabase(@NonNull AttachmentDatabase attachmentDatabase,
                                                 @NonNull List<Attachment> attachments)
            throws UndeliverableMessageException
    {
        List<Attachment> results = new ArrayList<>(attachments.size());

        for (Attachment attachment : attachments) {
            results.add(scaleAndStripExifToDatabase(attachmentDatabase, (DatabaseAttachment) attachment, null));
        }

        return results;
    }

    DatabaseAttachment scaleAndStripExifToDatabase(@NonNull AttachmentDatabase attachmentDatabase,
                                                   @NonNull DatabaseAttachment attachment,
                                                   @Nullable ProgressListener transcodeProgressListener)
            throws UndeliverableMessageException
    {
        try {
            if (MediaUtil.isVideo(attachment) && MediaConstraints.isVideoTranscodeAvailable()) {
                return transcodeVideoIfNeededToDatabase(attachmentDatabase, attachment, transcodeProgressListener);
            } else if (constraints.isSatisfied(context, attachment)) {
                if (MediaUtil.isJpeg(attachment)) {
                    MediaStream stripped = getResizedMedia(context, attachment);
                    return attachmentDatabase.updateAttachmentData(attachment, stripped);
                } else {
                    return attachment;
                }
            } else if (constraints.canResize(attachment)) {
                MediaStream resized = getResizedMedia(context, attachment);
                return attachmentDatabase.updateAttachmentData(attachment, resized);
            } else {
                throw new UndeliverableMessageException("Size constraints could not be met!");
            }
        } catch (IOException | MmsException e) {
            throw new UndeliverableMessageException(e);
        }
    }

    @RequiresApi(26)
    private @NonNull DatabaseAttachment transcodeVideoIfNeededToDatabase(@NonNull AttachmentDatabase attachmentDatabase,
                                                                         @NonNull DatabaseAttachment attachment,
                                                                         @Nullable ProgressListener progressListener)
            throws UndeliverableMessageException
    {
        try (NotificationController notification = GenericForegroundService.startForegroundTask(context, context.getString(R.string.AttachmentUploadJob_compressing_video_start))) {

            notification.setIndeterminateProgress();

            try (MediaDataSource dataSource = attachmentDatabase.mediaDataSourceFor(attachment.getAttachmentId())) {

                if (dataSource == null) {
                    throw new UndeliverableMessageException("Cannot get media data source for attachment.");
                }

                try (InMemoryTranscoder transcoder = new InMemoryTranscoder(context, dataSource, constraints.getCompressedVideoMaxSize(context))) {

                    if (transcoder.isTranscodeRequired()) {

                        MediaStream mediaStream = transcoder.transcode(percent -> {
                            notification.setProgress(100, percent);

                            if (progressListener != null) {
                                progressListener.onProgress(percent, 100);
                            }
                        });

                        return attachmentDatabase.updateAttachmentData(attachment, mediaStream);
                    } else {
                        return attachment;
                    }
                }
            }
        } catch (IOException | MmsException | BadVideoException e) {
            throw new UndeliverableMessageException("Failed to transcode", e);
        }
    }

    private MediaStream getResizedMedia(@NonNull Context context, @NonNull Attachment attachment)
            throws IOException
    {
        if (!constraints.canResize(attachment)) {
            throw new UnsupportedOperationException("Cannot resize this content type");
        }

        try {
            // XXX - This is loading everything into memory! We want the send path to be stream-like.
            BitmapUtil.ScaleResult scaleResult = BitmapUtil.createScaledBytes(context, new DecryptableStreamUriLoader.DecryptableUri(attachment.getDataUri()), constraints);
            return new MediaStream(new ByteArrayInputStream(scaleResult.getBitmap()), MediaUtil.IMAGE_JPEG, scaleResult.getWidth(), scaleResult.getHeight());
        } catch (BitmapDecodingException e) {
            throw new IOException(e);
        }
    }

    public interface ProgressListener {

        void onProgress(long progress, long total);
    }
}