package su.sres.securesms.util;

import android.app.Activity;

import su.sres.securesms.R;

public class DynamicNoActionBarInviteTheme extends DynamicTheme {
    @Override
    protected int getSelectedTheme(Activity activity) {
        String theme = TextSecurePreferences.getTheme(activity);

        if (theme.equals("dark")) return R.style.Signal_NoActionBar_Invite;

        return R.style.Signal_Light_NoActionBar_Invite;
    }
}