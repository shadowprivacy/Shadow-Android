package su.sres.securesms.jobs;

import androidx.annotation.NonNull;

import su.sres.securesms.BuildConfig;
import su.sres.securesms.TextSecureExpiredException;
import su.sres.securesms.attachments.Attachment;
import su.sres.securesms.database.AttachmentDatabase;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.logging.Log;
import su.sres.securesms.mms.MediaConstraints;
import su.sres.securesms.transport.UndeliverableMessageException;
import su.sres.securesms.util.Util;

import java.util.List;

public abstract class SendJob extends BaseJob {

  @SuppressWarnings("unused")
  private final static String TAG = SendJob.class.getSimpleName();

  public SendJob(Job.Parameters parameters) {
    super(parameters);
  }

  @Override
  public final void onRun() throws Exception {
    if (Util.getDaysTillBuildExpiry() <= 0) {
      throw new TextSecureExpiredException(String.format("TextSecure expired (build %d, now %d)",
                                                         BuildConfig.BUILD_TIMESTAMP,
                                                         System.currentTimeMillis()));
    }

    Log.i(TAG, "Starting message send attempt");
    onSend();
    Log.i(TAG, "Message send completed");
  }

  protected abstract void onSend() throws Exception;

  protected void markAttachmentsUploaded(long messageId, @NonNull List<Attachment> attachments) {
    AttachmentDatabase database = DatabaseFactory.getAttachmentDatabase(context);

    for (Attachment attachment : attachments) {
      database.markAttachmentUploaded(messageId, attachment);
    }
  }

  protected List<Attachment> scaleAndStripExifFromAttachments(@NonNull MediaConstraints constraints,
                                                              @NonNull List<Attachment> attachments)
      throws UndeliverableMessageException
  {
    MediaResizer       mediaResizer       = new MediaResizer(context, constraints);
    AttachmentDatabase attachmentDatabase = DatabaseFactory.getAttachmentDatabase(context);
    return mediaResizer.scaleAndStripExifToDatabase(attachmentDatabase, attachments);
  }
}
