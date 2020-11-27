package su.sres.securesms.logsubmit;

import android.content.Context;

import androidx.annotation.NonNull;

import su.sres.securesms.AppCapabilities;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.util.TextSecurePreferences;
import su.sres.signalservice.api.profiles.SignalServiceProfile;

public final class LogSectionCapabilities implements LogSection {

    @Override
    public @NonNull String getTitle() {
        return "CAPABILITIES";
    }

    @Override
    public @NonNull CharSequence getContent(@NonNull Context context) {
        if (!TextSecurePreferences.isPushRegistered(context)) {
            return "Unregistered";
        }

        if (TextSecurePreferences.getLocalNumber(context) == null || TextSecurePreferences.getLocalUuid(context) == null) {
            return "Self not yet available!";
        }

        Recipient self = Recipient.self();

        SignalServiceProfile.Capabilities capabilities = AppCapabilities.getCapabilities(false);

        return new StringBuilder().append("Local device UUID : ").append(capabilities.isUuid()).append("\n")
                .append("Global UUID       : ").append(self.getUuidCapability()).append("\n")
                .append("Local device GV2  : ").append(capabilities.isGv2()).append("\n")
                .append("Global GV2        : ").append(self.getGroupsV2Capability()).append("\n");
    }
}