package su.sres.securesms.mms;

import android.content.Context;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;

import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.keyvalue.ServiceConfigurationValues;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.securesms.util.Util;

public class PushMediaConstraints extends MediaConstraints {

  private static final ServiceConfigurationValues config = SignalStore.serviceConfigurationValues();

  // private static final int MAX_IMAGE_DIMEN_LOWMEM = 768;
  private static final int KB                     = 1024;
  private static final int MB                     = 1024 * KB;

  // private static final int[] FALLBACKS_LOWMEM = { MAX_IMAGE_DIMEN_LOWMEM, 512 };

  private final MediaConfig currentConfig;

  public PushMediaConstraints(@Nullable SentMediaQuality sentMediaQuality) {
    currentConfig = getCurrentConfig(ApplicationDependencies.getApplication(), sentMediaQuality);
  }

  @Override
  public int getImageMaxWidth(Context context) {
    // return Util.isLowMemory(context) ? MAX_IMAGE_DIMEN_LOWMEM : config.getImageMaxDimension();
    return currentConfig.imageSizeTargets[0];
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
  public int[] getImageDimensionTargets(Context context) {
    // return Util.isLowMemory(context) ? FALLBACKS_LOWMEM : new int[]{config.getImageMaxDimension(), 1024, 768, 512};
    return currentConfig.imageSizeTargets;
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
    return isVideoTranscodeAvailable() ? 5 * getVideoMaxSize(context)
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

  @Override
  public int getImageCompressionQualitySetting(@NonNull Context context) {
    return currentConfig.qualitySetting;
  }

  private static @NonNull MediaConfig getCurrentConfig(@NonNull Context context, @Nullable SentMediaQuality sentMediaQuality) {
    if (Util.isLowMemory(context)) {
      return MediaConfig.LEVEL_1_LOW_MEMORY;
    }

    // effectively circumvent the MQ levels in the code, treating everything as HIGH
    // if (sentMediaQuality == SentMediaQuality.HIGH) {
    //  return MediaConfig.LEVEL_3;
    // }

    return MediaConfig.getDefault(context);
  }

  public enum MediaConfig {
    LEVEL_1_LOW_MEMORY(true, 1, config.getImageMaxSize(), new int[] { 768, 512 }, 70),

    // LEVEL_1(false, 1, config.getImageMaxSize(), new int[] { 1600, 1024, 768, 512 }, 70),
    // LEVEL_2(false, 2, config.getImageMaxSize(), new int[] { 2048, 1600, 1024, 768, 512 }, 75),
    // max dimension currently hardcoded to 4096
    LEVEL_3(false, 3, config.getImageMaxSize(), new int[] { config.getImageMaxDimension(), 3072, 2048, 1600, 1024, 768, 512 }, 75);

    private final boolean isLowMemory;
    private final int     level;
    private final int     maxImageFileSize;
    private final int[]   imageSizeTargets;
    private final int     qualitySetting;

    MediaConfig(boolean isLowMemory,
                int level,
                int maxImageFileSize,
                @NonNull int[] imageSizeTargets,
                @IntRange(from = 0, to = 100) int qualitySetting)
    {
      this.isLowMemory      = isLowMemory;
      this.level            = level;
      this.maxImageFileSize = maxImageFileSize;
      this.imageSizeTargets = imageSizeTargets;
      this.qualitySetting   = qualitySetting;
    }

    public static @Nullable MediaConfig forLevel(int level) {
      boolean isLowMemory = Util.isLowMemory(ApplicationDependencies.getApplication());

      return Arrays.stream(values())
              .filter(v -> v.level == level && v.isLowMemory == isLowMemory)
              .findFirst()
              .orElse(null);
    }

    public static @NonNull MediaConfig getDefault(Context context) {
      return Util.isLowMemory(context) ? LEVEL_1_LOW_MEMORY : LEVEL_3;
    }
  }
}
