package su.sres.securesms.keyvalue;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class ServiceConfigurationValues extends SignalStoreValues {

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

    ServiceConfigurationValues(@NonNull KeyValueStore store) {
        super(store);
    }

    @Override
    void onFirstEverAppLaunch() {}

    public synchronized void setShadowUrl(String shadowUrl) {
        putString(SHADOW_SERVICE_URL, shadowUrl);
    }

    public synchronized void setCloudUrl(String cloudUrl) {
        putString(CLOUD_URL, cloudUrl);
    }

    public synchronized void setCloud2Url(String cloud2Url) {
        putString(CLOUD2_URL, cloud2Url);
    }

    public synchronized void setStorageUrl(String storageUrl) {
        putString(STORAGE_URL, storageUrl);
    }

    public synchronized void setStatusUrl(String statusUrl) {
        putString(STATUS_URL, statusUrl);
    }

    public synchronized void setUnidentifiedAccessCaPublicKey(byte[] unidentifiedAccessCaPublicKey) {
        putBlob(UNIDENTIFIED_ACCESS_CA_PUBLIC_KEY, unidentifiedAccessCaPublicKey);
    }

    public synchronized void setZkPublicKey(byte[] zkPublicKey) {
        putBlob(ZK_PUBLIC_KEY, zkPublicKey);
    }

    public synchronized void setCurrentCertVer(int certVer) {
        putInteger(CURRENT_CERT_VERSION, certVer);
    }

    public synchronized void setCurrentDirVer(long dirVer) {
       putLong(CURRENT_DIR_VERSION, dirVer);
    }

    public synchronized void setSupportEmail(String supportEmail) {
        putString(SUPPORT_EMAIL, supportEmail);
    }

    public synchronized void setLicensed(boolean isLicensed) {
        putBoolean(IS_LICENSED, isLicensed);
    }

    public synchronized void setTrialStatus(int status) {
        putInteger(TRIAL_STATUS, status);
    }

    public synchronized void setTrialStartTime(long timestamp) {
        putLong(TRIAL_START_TIME, timestamp);
    }

    public synchronized void setTrialDuration(int duration) {
        putInteger(TRIAL_DURATION, duration);
    }

    public synchronized void storeLicense(byte [] license) {
        putBlob(LICENSE, license);
    }

    public synchronized void removeLicense() {
        getStore().beginWrite()
                  .remove(LICENSE)
                  .commit();
    }

    public synchronized void setFcmSenderId(String senderId) {
        putString(FCM_SENDER_ID, senderId);
    }

    public @Nullable
    String getShadowUrl() {
        return getString(SHADOW_SERVICE_URL, EXAMPLE_URI);
    }

    public @Nullable
    String getCloudUrl() {
        return getString(CLOUD_URL, EXAMPLE_URI);
    }

    public @Nullable
    String getCloud2Url() {
        return getString(CLOUD2_URL, EXAMPLE_URI);
    }

    public @Nullable
    String getStorageUrl() {
        return getString(STORAGE_URL, EXAMPLE_URI);
    }

    public @Nullable
    String getStatusUrl() {
        return getString(STATUS_URL, EXAMPLE_URI);
    }

    public @Nullable
    byte[] getUnidentifiedAccessCaPublicKey() {
        return getBlob(UNIDENTIFIED_ACCESS_CA_PUBLIC_KEY, null);
    }

    public @Nullable
    byte[] getZkPublicKey() {
        return getBlob(ZK_PUBLIC_KEY, new byte[161]);
    }

    public int getCurrentCertVer() {
        return getInteger(CURRENT_CERT_VERSION, 1);
    }

    public long getCurrentDirVer() {
        return getLong(CURRENT_DIR_VERSION, 0);
    }

    public String getSupportEmail() {
        return getString(SUPPORT_EMAIL, "example@example.com");
    }

    public boolean isLicensed() {
       return getBoolean(IS_LICENSED, false);
    }

    public @Nullable byte [] retrieveLicense() {
        return getBlob(LICENSE, null);
    }

    public int getTrialStatus() {
        return getInteger(TRIAL_STATUS, 0);
    }

    public long getTrialStartTime() {
        return getLong(TRIAL_START_TIME, System.currentTimeMillis());
    }

    public int getTrialDuration() {
        return getInteger(TRIAL_DURATION, 14);
    }

    public String getFcmSenderId() {
        return getString(FCM_SENDER_ID, "null");
    }
}