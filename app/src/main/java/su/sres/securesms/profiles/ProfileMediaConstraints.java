package su.sres.securesms.profiles;


import android.content.Context;

import su.sres.securesms.mms.MediaConstraints;

public class ProfileMediaConstraints extends MediaConstraints {
  @Override
  public int getImageMaxWidth(Context context) {
    return 640;
  }

  @Override
  public int getImageMaxHeight(Context context) {
    return 640;
  }

  @Override
  public long getImageMaxSize(Context context) {
    return 5 * 1024 * 1024;
  }

  @Override
  public long getGifMaxSize(Context context) {
    return 0;
  }

  @Override
  public long getVideoMaxSize(Context context) {
    return 0;
  }

  @Override
  public long getAudioMaxSize(Context context) {
    return 0;
  }

  @Override
  public long getDocumentMaxSize(Context context) {
    return 0;
  }
}
