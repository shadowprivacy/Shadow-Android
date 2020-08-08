package su.sres.securesms.keyvalue;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceDataStore;

import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.logging.SignalUncaughtExceptionHandler;

/**
 * Simple, encrypted key-value store.
 */
public final class SignalStore {

    private static final String LAST_PREKEY_REFRESH_TIME      = "last_prekey_refresh_time";
    private static final String MESSAGE_REQUEST_ENABLE_TIME   = "message_request_enable_time";
    private static final String LAST_CERT_REFRESH_TIME        = "last_cert_refresh_time";
    private static final String LAST_LICENSE_REFRESH_TIME     = "last_license_refresh_time";

    private SignalStore() {}

    public static void onFirstEverAppLaunch() {
        registrationValues().onFirstEverAppLaunch();
    }

    public static @NonNull KbsValues kbsValues() {
        return new KbsValues(getStore());
    }

    public static @NonNull RegistrationValues registrationValues() {
        return new RegistrationValues(getStore());
    }

    public static @NonNull ServiceConfigurationValues serviceConfigurationValues() {
        return new ServiceConfigurationValues(getStore());
    }

    public static @NonNull RemoteConfigValues remoteConfigValues() {
        return new RemoteConfigValues(getStore());
    }

    public static @NonNull StorageServiceValues storageServiceValues() {
        return new StorageServiceValues(getStore());
    }

    public static @NonNull GroupsV2AuthorizationSignalStoreCache groupsV2AuthorizationCache() {
        return new GroupsV2AuthorizationSignalStoreCache(getStore());
    }

    public static long getLastPrekeyRefreshTime() {
        return getStore().getLong(LAST_PREKEY_REFRESH_TIME, 0);
    }

    public static void setLastPrekeyRefreshTime(long time) {
        putLong(LAST_PREKEY_REFRESH_TIME, time);
    }

    public static long getLastCertRefreshTime() {
        return getStore().getLong(LAST_CERT_REFRESH_TIME, 0);
    }

    public static void setLastCertRefreshTime(long time) {
        putLong(LAST_CERT_REFRESH_TIME, time);
    }

    public static long getLastLicenseRefreshTime() {
        return getStore().getLong(LAST_LICENSE_REFRESH_TIME, 0);
    }

    public static void setLastLicenseRefreshTime(long time) {
        putLong(LAST_LICENSE_REFRESH_TIME, time);
    }

    public static long getMessageRequestEnableTime() {
        return getStore().getLong(MESSAGE_REQUEST_ENABLE_TIME, 0);
    }

    public static void setMessageRequestEnableTime(long time) {
        putLong(MESSAGE_REQUEST_ENABLE_TIME, time);
    }

    public static @NonNull
    PreferenceDataStore getPreferenceDataStore() {
        return new SignalPreferenceDataStore(getStore());
    }

    /**
     * Ensures any pending writes are finished. Only intended to be called by
     * {@link SignalUncaughtExceptionHandler}.
     */
    public static void blockUntilAllWritesFinished() {
        getStore().blockUntilAllWritesFinished();
    }

    private static @NonNull KeyValueStore getStore() {
        return ApplicationDependencies.getKeyValueStore();
    }

    private static void putBlob(@NonNull String key, byte[] value) {
        getStore().beginWrite().putBlob(key, value).apply();
    }

    private static void putBoolean(@NonNull String key, boolean value) {
        getStore().beginWrite().putBoolean(key, value).apply();
    }

    private static void putFloat(@NonNull String key, float value) {
        getStore().beginWrite().putFloat(key, value).apply();
    }

    private static void putInteger(@NonNull String key, int value) {
        getStore().beginWrite().putInteger(key, value).apply();
    }

    private static void putLong(@NonNull String key, long value) {
        getStore().beginWrite().putLong(key, value).apply();
    }

    private static void putString(@NonNull String key, String value) {
        getStore().beginWrite().putString(key, value).apply();
    }
}