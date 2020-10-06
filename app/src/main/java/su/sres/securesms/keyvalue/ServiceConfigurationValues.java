package su.sres.securesms.keyvalue;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class ServiceConfigurationValues {

    private static final String SHADOW_SERVICE_URL                = "service_confifuration.shadow_service_url";
    private static final String CLOUD_URL                         = "service_configuration.cloud_url";
    private static final String CLOUD2_URL                        = "service_configuration.cloud2_url";
    private static final String FCM_SENDER_ID                     = "service_configuration.fcm_sender_id";
    private static final String STORAGE_URL                       = "service_configuration.storage_url";
    private static final String STATUS_URL                        = "service_configuration.status_url";
    private static final String UNIDENTIFIED_ACCESS_CA_PUBLIC_KEY = "service_configuration.unidentified_access_ca_public_key";
    private static final String ZK_PUBLIC_KEY                     = "service_configuration.zk_public_key";
    private static final String CURRENT_CERT_VERSION              = "service_configuration.current_cert_version";
    private static final String CURRENT_DIR_VERSION               = "service_configuration.current_dir_version";
    private static final String SUPPORT_EMAIL                     = "service_configuration.support_email";
    private static final String IS_LICENSED                       = "service_configuration.is_licensed";
    private static final String LICENSE                           = "service_configuration.license";

    private static final String TRIAL_STATUS                      = "service_configuration.trial_status";
    private static final String TRIAL_START_TIME                  = "service_configuration.trial_start_time";
    private static final String TRIAL_DURATION                    = "service_configuration.trial_duration";

    public static final String EXAMPLE_URI                        = "https://example.com";

    private final KeyValueStore store;

    ServiceConfigurationValues(@NonNull KeyValueStore store) {
        this.store = store;
    }

    public synchronized void setShadowUrl(String shadowUrl) {
        store.beginWrite()
                .putString(SHADOW_SERVICE_URL, shadowUrl)
                .commit();
    }

    public synchronized void setCloudUrl(String cloudUrl) {
        store.beginWrite()
                .putString(CLOUD_URL, cloudUrl)
                .commit();
    }

    public synchronized void setCloud2Url(String cloud2Url) {
        store.beginWrite()
                .putString(CLOUD2_URL, cloud2Url)
                .commit();
    }

    public synchronized void setStorageUrl(String storageUrl) {
        store.beginWrite()
                .putString(STORAGE_URL, storageUrl)
                .commit();
    }

    public synchronized void setStatusUrl(String statusUrl) {
        store.beginWrite()
                .putString(STATUS_URL, statusUrl)
                .commit();
    }

    public synchronized void setUnidentifiedAccessCaPublicKey(byte[] unidentifiedAccessCaPublicKey) {
        store.beginWrite()
                .putBlob(UNIDENTIFIED_ACCESS_CA_PUBLIC_KEY, unidentifiedAccessCaPublicKey)
                .commit();
    }

    public synchronized void setZkPublicKey(byte[] zkPublicKey) {
        store.beginWrite()
                .putBlob(ZK_PUBLIC_KEY, zkPublicKey)
                .commit();
    }

    public synchronized void setCurrentCertVer(int certVer) {
        store.beginWrite()
                .putInteger(CURRENT_CERT_VERSION, certVer)
                .commit();
    }

    public synchronized void setCurrentDirVer(long dirVer) {
        store.beginWrite()
                .putLong(CURRENT_DIR_VERSION, dirVer)
                .commit();
    }

    public synchronized void setSupportEmail(String supportEmail) {
        store.beginWrite()
                .putString(SUPPORT_EMAIL, supportEmail)
                .commit();
    }

    public synchronized void setLicensed(boolean isLicensed) {
        store.beginWrite()
                .putBoolean(IS_LICENSED, isLicensed)
                .commit();
    }

    public synchronized void setTrialStatus(int status) {
        store.beginWrite()
                .putInteger(TRIAL_STATUS, status)
                .commit();
    }

    public synchronized void setTrialStartTime(long timestamp) {
        store.beginWrite()
                .putLong(TRIAL_START_TIME, timestamp)
                .commit();
    }

    public synchronized void setTrialDuration(int duration) {
        store.beginWrite()
                .putInteger(TRIAL_DURATION, duration)
                .commit();
    }

    public synchronized void storeLicense(byte [] license) {
        store.beginWrite()
                .putBlob(LICENSE, license)
                .commit();
    }

    public synchronized void removeLicense() {
        store.beginWrite()
                .remove(LICENSE)
                .commit();
    }

    public synchronized void setFcmSenderId(String senderId) {
        store.beginWrite()
                .putString(FCM_SENDER_ID, senderId)
                .commit();
    }

    public @Nullable
    String getShadowUrl() {
        return store.getString(SHADOW_SERVICE_URL, EXAMPLE_URI);
    }

    public @Nullable
    String getCloudUrl() {
        return store.getString(CLOUD_URL, EXAMPLE_URI);
    }

    public @Nullable
    String getCloud2Url() {
        return store.getString(CLOUD2_URL, EXAMPLE_URI);
    }

    public @Nullable
    String getStorageUrl() {
        return store.getString(STORAGE_URL, EXAMPLE_URI);
    }

    public @Nullable
    String getStatusUrl() {
        return store.getString(STATUS_URL, EXAMPLE_URI);
    }

    public @Nullable
    byte[] getUnidentifiedAccessCaPublicKey() {
        return store.getBlob(UNIDENTIFIED_ACCESS_CA_PUBLIC_KEY, null);
    }

    public @Nullable
    byte[] getZkPublicKey() {
        return store.getBlob(ZK_PUBLIC_KEY, new byte[161]);
    }

    public int getCurrentCertVer() {
        return store.getInteger(CURRENT_CERT_VERSION, 1);
    }

    public long getCurrentDirVer() {
        return store.getLong(CURRENT_DIR_VERSION, 0);
    }

    public String getSupportEmail() {
        return store.getString(SUPPORT_EMAIL, "example@example.com");
    }

    public boolean isLicensed() {
       return store.getBoolean(IS_LICENSED, false);
    }

    public @Nullable byte [] retrieveLicense() {
        return store.getBlob(LICENSE, null);
    }

    public int getTrialStatus() {
        return store.getInteger(TRIAL_STATUS, 0);
    }

    public long getTrialStartTime() {
        return store.getLong(TRIAL_START_TIME, System.currentTimeMillis());
    }

    public int getTrialDuration() {
        return store.getInteger(TRIAL_DURATION, 14);
    }

    public String getFcmSenderId() {
        return store.getString(FCM_SENDER_ID, "null");
    }
}