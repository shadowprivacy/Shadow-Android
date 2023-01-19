package su.sres.securesms.util;

import androidx.annotation.StyleRes;

import su.sres.securesms.R;

public class DynamicNoActionBarInviteTheme extends DynamicTheme {
    protected @StyleRes int getTheme() {
        return R.style.Signal_DayNight_Invite;
    }
}