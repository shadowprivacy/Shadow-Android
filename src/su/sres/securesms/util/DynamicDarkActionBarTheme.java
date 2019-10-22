package su.sres.securesms.util;

import android.app.Activity;

import su.sres.securesms.R;

public class DynamicDarkActionBarTheme extends DynamicTheme {
    @Override
    protected int getSelectedTheme(Activity activity) {
        String theme = TextSecurePreferences.getTheme(activity);

        if (theme.equals("dark")) {
            return R.style.TextSecure_DarkTheme_Conversation;
        }

        return R.style.TextSecure_LightTheme_Conversation;
    }
}