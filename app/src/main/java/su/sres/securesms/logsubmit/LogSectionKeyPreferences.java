package su.sres.securesms.logsubmit;

import android.content.Context;

import androidx.annotation.NonNull;

import su.sres.securesms.keyvalue.SignalStore;
import su.sres.securesms.util.TextSecurePreferences;
import su.sres.securesms.util.Util;

final class LogSectionKeyPreferences implements LogSection {

    @Override
    public @NonNull String getTitle() {
        return "KEY PREFERENCES";
    }

    @Override
    public @NonNull CharSequence getContent(@NonNull Context context) {
        return new StringBuilder().append("Screen Lock          : ").append(TextSecurePreferences.isScreenLockEnabled(context)).append("\n")
                .append("Screen Lock Timeout  : ").append(TextSecurePreferences.getScreenLockTimeout(context)).append("\n")
                .append("Password Disabled    : ").append(TextSecurePreferences.isPasswordDisabled(context)).append("\n")
                .append("WiFi SMS             : ").append(TextSecurePreferences.isWifiSmsEnabled(context)).append("\n")
                .append("Default SMS          : ").append(Util.isDefaultSmsProvider(context)).append("\n")
                .append("Call Bandwidth Mode  : ").append(SignalStore.settings().getCallBandwidthMode()).append("\n")
                .append("Client Deprecated    : ").append(SignalStore.misc().isClientDeprecated()).append("\n");
    }
}
