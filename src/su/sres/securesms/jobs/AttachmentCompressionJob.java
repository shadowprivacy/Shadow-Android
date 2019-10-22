package su.sres.securesms.jobs;

import android.content.Context;
import android.media.MediaDataSource;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import org.greenrobot.eventbus.EventBus;

import su.sres.securesms.ExifTagBlacklist;
import su.sres.securesms.R;
import su.sres.securesms.attachments.Attachment;
import su.sres.securesms.attachments.AttachmentId;
import su.sres.securesms.attachments.DatabaseAttachment;
import su.sres.securesms.database.AttachmentDatabase;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.events.PartProgressEvent;
import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobmanager.impl.NetworkConstraint;
import su.sres.securesms.logging.Log;
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
import su.sres.securesms.util.MemoryFileDescriptor;
import su.sres.securesms.util.MemoryFileDescriptor.MemoryFileException;
import su.sres.securesms.video.InMemoryTranscoder;
import su.sres.securesms.video.VideoSizeException;
import su.sres.securesms.video.VideoSourceException;
import su.sres.securesms.video.videoconverter.EncodingException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public final class AttachmentCompressionJob extends BaseJob {

    public static final String KEY = "AttachmentCompressionJob";

    @SuppressWarnings("unused")
    private static final String TAG = Log.tag(AttachmentCompressionJob.class);

    private static final String KEY_ROW_ID              = "row_id";
    private static final String KEY_UNIQUE_ID           = "unique_id";
    private static final String KEY_MMS                 = "mms";
    private static final String KEY_MMS_SUBSCRIPTION_ID = "mms_subscription_id";

    private final AttachmentId attachmentId;
    private final boolean      mms;
    private final int          mmsSubscriptionId;

    public static AttachmentCompressionJob fromAttachment(@NonNull DatabaseAttachment databaseAttachment,
                                                          boolean mms,
                                                          int mmsSubscriptionId)
    {
        return new AttachmentCompressionJob(databaseAttachment.getAttachmentId(),
                MediaUtil.isVideo(databaseAttachment) && MediaConstraints.isVideoTranscodeAvailable(),
                mms,
                mmsSubscriptionId);
    }

    private AttachmentCompressionJob(@NonNull AttachmentId attachmentId,
                                     boolean isVideoTranscode,
                                     boolean mms,
                                     int mmsSubscriptionId)
    {
        this(new Parameters.Builder()
                        .addConstraint(NetworkConstraint.KEY)
                        .setLifespan(TimeUnit.DAYS.toMillis(1))
                        .setMaxAttempts(Parameters.UNLIMITED)
                        .setQueue(isVideoTranscode ? "VIDEO_TRANSCODE" : null)
                        .build(),
                attachmentId,
                mms,
                mmsSubscriptionId);
    }

    private AttachmentCompressionJob(@NonNull Parameters parameters,
                                     @NonNull AttachmentId attachmentId,
                                     boolean mms,
                                     int mmsSubscriptionId)
    {
        super(parameters);
        this.attachmentId      = attachmentId;
        this.mms               = mms;
        this.mmsSubscriptionId = mmsSubscriptionId;
    }

    @Override
    public @NonNull Data serialize() {
        return new Data.Builder().putLong(KEY_ROW_ID, attachmentId.getRowId())
                .putLong(KEY_UNIQUE_ID, attachmentId.getUniqueId())
                .putBoolean(KEY_MMS, mms)
                .putInt(KEY_MMS_SUBSCRIPTION_ID, mmsSubscriptionId)
                .build();
    }

    @Override
    public @NonNull String getFactoryKey() {
        return KEY;
    }

    @Override
    public void onRun() throws Exception {
        AttachmentDatabase         database           = DatabaseFactory.getAttachmentDatabase(context);
        DatabaseAttachment         databaseAttachment = database.getAttachment(attachmentId);

        if (databaseAttachment == null) {
            throw new UndeliverableMessageException("Cannot find the specified attachment.");
        }

        MediaConstraints mediaConstraints = mms ? MediaConstraints.getMmsMediaConstraints(mmsSubscriptionId)
                : MediaConstraints.getPushMediaConstraints();

        scaleAndStripExif(database, mediaConstraints, databaseAttachment);
    }

    @Override
    public void onCanceled() { }

    @Override
    protected boolean onShouldRetry(@NonNull Exception exception) {
        return exception instanceof IOException;
    }

    private void scaleAndStripExif(@NonNull AttachmentDatabase attachmentDatabase,
                                   @NonNull MediaConstraints constraints,
                                   @NonNull DatabaseAttachment attachment)
            throws UndeliverableMessageException
    {
        try {
            if (MediaUtil.isVideo(attachment) && MediaConstraints.isVideoTranscodeAvailable()) {
                transcodeVideoIfNeededToDatabase(context, attachmentDatabase, attachment, constraints, EventBus.getDefault());
            } else if (constraints.isSatisfied(context, attachment)) {
                if (MediaUtil.isJpeg(attachment) && ExifTagBlacklist.hasViolations(attachmentDatabase.getAttachmentStream(attachmentId, 0))) {
                    MediaStream stripped = getResizedMedia(context, attachment, constraints);
                    attachmentDatabase.updateAttachmentData(attachment, stripped);
                }
            } else if (constraints.canResize(attachment)) {
                MediaStream resized = getResizedMedia(context, attachment, constraints);
                attachmentDatabase.updateAttachmentData(attachment, resized);
            } else {
                throw new UndeliverableMessageException("Size constraints could not be met!");
            }
        } catch (IOException | MmsException e) {
            throw new UndeliverableMessageException(e);
        }
    }

    @RequiresApi(26)
    private static void transcodeVideoIfNeededToDatabase(@NonNull Context context,
                                                         @NonNull AttachmentDatabase attachmentDatabase,
                                                         @NonNull DatabaseAttachment attachment,
                                                         @NonNull MediaConstraints constraints,
                                                         @NonNull EventBus eventBus)
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
                            eventBus.postSticky(new PartProgressEvent(attachment,
                                    PartProgressEvent.Type.COMPRESSION,
                                    100,
                                    percent));
                        });

                        attachmentDatabase.updateAttachmentData(attachment, mediaStream);
                    }
                }
            }
        } catch (VideoSourceException | EncodingException | MemoryFileException e) {
            if (attachment.getSize() > constraints.getVideoMaxSize(context)) {
                throw new UndeliverableMessageException("Duration not found, attachment too large to skip transcode", e);
            } else {
                Log.w(TAG, "Problem with video source, but video small enough to skip transcode", e);
            }
        } catch (IOException | MmsException | VideoSizeException e) {
            throw new UndeliverableMessageException("Failed to transcode", e);
        }
    }

    private static MediaStream getResizedMedia(@NonNull Context context,
                                               @NonNull Attachment attachment,
                                               @NonNull MediaConstraints constraints)
            throws IOException
    {
        if (!constraints.canResize(attachment)) {
            throw new UnsupportedOperationException("Cannot resize this content type");
        }

        try {
            BitmapUtil.ScaleResult scaleResult = BitmapUtil.createScaledBytes(context,
                    new DecryptableStreamUriLoader.DecryptableUri(attachment.getDataUri()),
                    constraints);

            return new MediaStream(new ByteArrayInputStream(scaleResult.getBitmap()),
                    MediaUtil.IMAGE_JPEG,
                    scaleResult.getWidth(),
                    scaleResult.getHeight());
        } catch (BitmapDecodingException e) {
            throw new IOException(e);
        }
    }

    public static final class Factory implements Job.Factory<AttachmentCompressionJob> {
        @Override
        public @NonNull AttachmentCompressionJob create(@NonNull Parameters parameters, @NonNull Data data) {
            return new AttachmentCompressionJob(parameters,
                    new AttachmentId(data.getLong(KEY_ROW_ID), data.getLong(KEY_UNIQUE_ID)),
                    data.getBoolean(KEY_MMS),
                    data.getInt(KEY_MMS_SUBSCRIPTION_ID));
        }
    }
}