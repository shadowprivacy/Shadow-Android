package su.sres.securesms.keyvalue;

import android.content.Context;

import androidx.annotation.NonNull;

import su.sres.securesms.phonenumbers.PhoneNumberFormatter;
import su.sres.securesms.util.Util;

public final class OnboardingValues extends SignalStoreValues {

    private static final String SHOW_NEW_GROUP      = "onboarding.new_group";

    OnboardingValues(@NonNull KeyValueStore store) {
        super(store);
    }

    @Override
    void onFirstEverAppLaunch() {
        putBoolean(SHOW_NEW_GROUP, true);
    }

    public void clearAll() {
        setShowNewGroup(false);
    }

    public boolean hasOnboarding(@NonNull Context context) {
        return shouldShowNewGroup();
    }

    public void setShowNewGroup(boolean value) {
        putBoolean(SHOW_NEW_GROUP, value);
    }

    public boolean shouldShowNewGroup() {
        return getBoolean(SHOW_NEW_GROUP, false);
    }

}
