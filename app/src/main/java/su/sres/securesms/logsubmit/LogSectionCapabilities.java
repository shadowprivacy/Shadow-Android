package su.sres.securesms.logsubmit;

import android.content.Context;

import androidx.annotation.NonNull;

import su.sres.securesms.AppCapabilities;
import su.sres.securesms.recipients.Recipient;
import su.sres.signalservice.api.profiles.SignalServiceProfile;

public final class LogSectionCapabilities implements LogSection {

    @Override
    public @NonNull String getTitle() {
        return "CAPABILITIES";
    }

    @Override
    public @NonNull CharSequence getContent(@NonNull Context context) {
        Recipient self = Recipient.self();
        if (!self.isRegistered()) {
            return "Unregistered";
        } else {
            SignalServiceProfile.Capabilities capabilities = AppCapabilities.getCapabilities(false);

            return new StringBuilder().append("Local device UUID : ").append(capabilities.isUuid()).append("\n")
                    .append("Global UUID       : ").append(self.getUuidCapability()).append("\n")
                    .append("Local device GV2  : ").append(capabilities.isGv2()).append("\n")
                    .append("Global GV2        : ").append(self.getGroupsV2Capability()).append("\n");
        }
    }
}