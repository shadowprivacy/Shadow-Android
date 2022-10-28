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
  public int getImageMaxSize(Context context) {
    return config.getImageMaxSize();
  }

  @Override
  public int getGifMaxSize(Context context) {
    return config.getGifMaxSize();
  }

  @Override
  public int getVideoMaxSize(Context context) {
    return config.getVideoMaxSize();
  }

  @Override
  public int getUncompressedVideoMaxSize(Context context) {
    return isVideoTranscodeAvailable() ? 2 * getVideoMaxSize(context)
            : getVideoMaxSize(context);
  }

  @Override
  public int getCompressedVideoMaxSize(Context context) {
    // on low memory devices the transcoder will fail with large video files due to this
    return (int) (Util.isLowMemory(context) ? 30 * MB
                : 0.5 * getVideoMaxSize(context));
  }

  @Override
  public int getAudioMaxSize(Context context) {
    return config.getAudioMaxSize();
  }

  @Override
  public int getDocumentMaxSize(Context context) {
    return config.getDocMaxSize();
  }
}
