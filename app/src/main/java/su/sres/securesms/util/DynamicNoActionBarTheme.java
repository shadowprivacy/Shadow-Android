package su.sres.securesms.util;

import androidx.annotation.StyleRes;

import su.sres.securesms.R;

public class DynamicNoActionBarTheme extends DynamicTheme {
  protected @StyleRes int getLightThemeStyle() {

    return R.style.TextSecure_LightNoActionBar;
  }

  protected @StyleRes int getDarkThemeStyle() {
    return R.style.TextSecure_DarkNoActionBar;
  }
}
