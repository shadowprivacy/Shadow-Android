package su.sres.securesms.mms;

import android.content.Context;

import su.sres.securesms.keyvalue.ServiceConfigurationValues;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.securesms.util.Util;

public class PushMediaConstraints extends MediaConstraints {

  private final ServiceConfigurationValues config = SignalStore.serviceConfigurationValues();

  private static final int MAX_IMAGE_DIMEN_LOWMEM = 768;
  private static final int MAX_IMAGE_DIMEN        = 4096;
  private static final int KB                     = 1024;
  private static final int MB                     = 1024 * KB;

  @Override
  public int getImageMaxWidth(Context context) {
    return Util.isLowMemory(context) ? MAX_IMAGE_DIMEN_LOWMEM : MAX_IMAGE_DIMEN;
  }

  @Override
  public int getImageMaxHeight(Context context) {
    return getImageMaxWidth(context);
  }

  @Override
  public long getImageMaxSize(Context context) {
    return (long) MB * config.getImageMaxSize();
  }

  @Override
  public long getGifMaxSize(Context context) {
    return (long) MB * config.getGifMaxSize();
  }

  @Override
  public long getVideoMaxSize(Context context) {
    return (long) MB * config.getVideoMaxSize();
  }

  @Override
  public long getUncompressedVideoMaxSize(Context context) {
    return isVideoTranscodeAvailable() ? 3 * getVideoMaxSize(context)
            : getVideoMaxSize(context);
  }

  @Override
  public long getCompressedVideoMaxSize(Context context) {
    // on low memory devices the transcoder will fail with large video files due to this
    return (long) (Util.isLowMemory(context) ? 30 * MB
                : 0.5 * getVideoMaxSize(context));
  }

  @Override
  public long getAudioMaxSize(Context context) {
    return (long) MB * config.getAudioMaxSize();
  }

  @Override
  public long getDocumentMaxSize(Context context) {
    return (long) MB * config.getDocMaxSize();
  }
}
