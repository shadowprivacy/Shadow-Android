package su.sres.securesms;

import su.sres.signalservice.api.profiles.SignalServiceProfile;

public final class AppCapabilities {

    private AppCapabilities() {
    }

    private static final boolean UUID_CAPABLE      = false;
    private static final boolean GROUPS_V2_CAPABLE = false;

    public static SignalServiceProfile.Capabilities getCapabilities() {
        return new SignalServiceProfile.Capabilities(UUID_CAPABLE,
                GROUPS_V2_CAPABLE);
    }
}