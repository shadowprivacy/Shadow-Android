package su.sres.securesms.util;

import androidx.annotation.StyleRes;

import su.sres.securesms.R;

public class DynamicDarkActionBarTheme extends DynamicTheme {
    protected @StyleRes int getLightThemeStyle() {

        return R.style.TextSecure_LightTheme_Conversation;
    }

    protected @StyleRes int getDarkThemeStyle() {
        return R.style.TextSecure_DarkTheme_Conversation;
    }
}