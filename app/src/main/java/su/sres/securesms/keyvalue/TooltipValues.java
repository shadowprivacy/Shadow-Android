package su.sres.securesms.keyvalue;

import androidx.annotation.NonNull;

import su.sres.signalservice.api.storage.StorageKey;

public class TooltipValues extends SignalStoreValues {

    private static final String BLUR_HUD_ICON   = "tooltip.blur_hud_icon";

    TooltipValues(@NonNull KeyValueStore store) {
        super(store);
    }

    @Override
    public void onFirstEverAppLaunch() {
    }

    public boolean hasSeenBlurHudIconTooltip() {
        return getBoolean(BLUR_HUD_ICON, false);
    }

    public void markBlurHudIconTooltipSeen() {
        putBoolean(BLUR_HUD_ICON, true);
    }
}