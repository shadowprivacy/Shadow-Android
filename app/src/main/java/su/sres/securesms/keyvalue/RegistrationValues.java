package su.sres.securesms.keyvalue;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;

public final class RegistrationValues extends SignalStoreValues {

    private static final String REGISTRATION_COMPLETE = "registration.complete";
    private static final String SERVER_SET            = "registration.server_set";
    private static final String TRUSTSTORE_PASSWORD   = "registration.truststore_password";

    RegistrationValues(@NonNull KeyValueStore store) {
        super(store);
    }

    public synchronized void onFirstEverAppLaunch() {
        getStore().beginWrite()
                  .putBoolean(REGISTRATION_COMPLETE, false)
                  .commit();
    }

    public synchronized void clearRegistrationComplete() {
        onFirstEverAppLaunch();
    }

    public synchronized void setRegistrationComplete() {
        getStore().beginWrite()
                  .putBoolean(REGISTRATION_COMPLETE, true)
                  .commit();
    }

    @CheckResult
    public synchronized boolean isRegistrationComplete() {
        return getStore().getBoolean(REGISTRATION_COMPLETE, true);
    }

    public String getStorePass() {
        return getString(TRUSTSTORE_PASSWORD, null);
    }

    public synchronized void setStorePass(String storePass) {
        putString(TRUSTSTORE_PASSWORD, storePass);
    }

    public synchronized void setServerSet(boolean flag) {
        putBoolean(SERVER_SET, flag);
    }

    public synchronized boolean isServerSet() {
        return getBoolean(SERVER_SET, false);
    }
}