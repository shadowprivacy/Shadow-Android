package su.sres.securesms.components.reminder;

import android.content.Context;

import su.sres.securesms.R;
import su.sres.securesms.keyvalue.SignalStore;

public class LicenseInvalidReminder extends Reminder {

    public LicenseInvalidReminder(final Context context) {
        super(context.getString(R.string.LicenseInvalidReminder_no_valid_license),
                context.getString(R.string.LicenseInvalidReminder_contact_your_administrator));
    }

    @Override
    public boolean isDismissable() {
        return false;
    }

    public static boolean isEligible() {
        return !SignalStore.serviceConfigurationValues().isLicensed();
    }
}
