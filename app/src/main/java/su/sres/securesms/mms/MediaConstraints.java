package su.sres.securesms.mms;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import androidx.annotation.NonNull;
import su.sres.core.util.logging.Log;
import android.util.Pair;

import su.sres.securesms.attachments.Attachment;
import su.sres.securesms.util.BitmapDecodingException;
import su.sres.securesms.util.BitmapUtil;
import su.sres.securesms.util.FeatureFlags;
import su.sres.securesms.util.MediaUtil;
import su.sres.securesms.util.MemoryFileDescriptor;

import java.io.IOException;
import java.io.InputStream;

public abstract class MediaConstraints {
  private static final String TAG = MediaConstraints.class.getSimpleName();

  public static MediaConstraints getPushMediaConstraints() {
    return new PushMediaConstraints();
  }

  public static MediaConstraints getMmsMediaConstraints(int subscriptionId) {
    return new MmsMediaConstraints(subscriptionId);
  }

  public abstract int getImageMaxWidth(Context context);
  public abstract int getImageMaxHeight(Context context);
  public abstract long getImageMaxSize(Context context);

  public abstract long getGifMaxSize(Context context);
  public abstract long getVideoMaxSize(Context context);

  public long getUncompressedVideoMaxSize(Context context) {
    return getVideoMaxSize(context);
  }

  public long getCompressedVideoMaxSize(Context context) {
    return getVideoMaxSize(context);
  }

  public abstract long getAudioMaxSize(Context context);
  public abstract long getDocumentMaxSize(Context context);

  public boolean isSatisfied(@NonNull Context context, @NonNull Attachment attachment) {
    try {
      return (MediaUtil.isGif(attachment)    && attachment.getSize() <= getGifMaxSize(context)   && isWithinBounds(context, attachment.getUri())) ||
             (MediaUtil.isImage(attachment)  && attachment.getSize() <= getImageMaxSize(context) && isWithinBounds(context, attachment.getUri())) ||
             (MediaUtil.isAudio(attachment)  && attachment.getSize() <= getAudioMaxSize(context)) ||
             (MediaUtil.isVideo(attachment)  && attachment.getSize() <= getVideoMaxSize(context)) ||
             (MediaUtil.isFile(attachment) && attachment.getSize() <= getDocumentMaxSize(context));
    } catch (IOException ioe) {
      Log.w(TAG, "Failed to determine if media's constraints are satisfied.", ioe);
      return false;
    }
  }

  private boolean isWithinBounds(Context context, Uri uri) throws IOException {
    try {
      InputStream is = PartAuthority.getAttachmentStream(context, uri);
      Pair<Integer, Integer> dimensions = BitmapUtil.getDimensions(is);
      return dimensions.first  > 0 && dimensions.first  <= getImageMaxWidth(context) &&
             dimensions.second > 0 && dimensions.second <= getImageMaxHeight(context);
    } catch (BitmapDecodingException e) {
      throw new IOException(e);
    }
  }

  public boolean canResize(@NonNull Attachment attachment) {
    return MediaUtil.isImage(attachment) && !MediaUtil.isGif(attachment) ||
            MediaUtil.isVideo(attachment) && isVideoTranscodeAvailable();
  }

  public static boolean isVideoTranscodeAvailable() {
    return Build.VERSION.SDK_INT >= 26 && (FeatureFlags.useStreamingVideoMuxer() || MemoryFileDescriptor.supported());
  }
}
