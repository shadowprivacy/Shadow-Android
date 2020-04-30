package su.sres.securesms.keyvalue;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;

public final class RegistrationValues {

    private static final String REGISTRATION_COMPLETE = "registration.complete";
    private static final String PIN_REQUIRED          = "registration.pin_required";
    private static final String SERVER_SET            = "registration.server_set";
    private static final String TRUSTSTORE_PASSWORD = "registration.truststore_password";

    private final KeyValueStore store;

    RegistrationValues(@NonNull KeyValueStore store) {
        this.store = store;
    }

    public synchronized void onFirstEverAppLaunch() {
        store.beginWrite()
                .putBoolean(REGISTRATION_COMPLETE, false)
//                .putBoolean(PIN_REQUIRED, true)
                .commit();
    }

    public synchronized void clearRegistrationComplete() {
        onFirstEverAppLaunch();
    }

    public synchronized void setRegistrationComplete() {
        store.beginWrite()
                .putBoolean(REGISTRATION_COMPLETE, true)
                .commit();
    }

/**    @CheckResult
    public synchronized boolean pinWasRequiredAtRegistration() {
        return store.getBoolean(PIN_REQUIRED, false);
    } */

    @CheckResult
    public synchronized boolean isRegistrationComplete() {
        return store.getBoolean(REGISTRATION_COMPLETE, true);
    }

    public String getStorePass() {
        return store.getString(TRUSTSTORE_PASSWORD, null);
    }

    public synchronized void setStorePass(String storePass) {
        store.beginWrite()
                .putString(TRUSTSTORE_PASSWORD, storePass)
                .commit();
    }

    public synchronized void setServerSet(boolean flag) {
        store.beginWrite()
                .putBoolean(SERVER_SET, flag)
                .commit();
    }

    public synchronized boolean isServerSet() {
        return store.getBoolean(SERVER_SET, false);
    }
}