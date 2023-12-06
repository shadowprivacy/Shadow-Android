package su.sres.securesms.keyvalue;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.List;

public final class ServiceConfigurationValues extends SignalStoreValues {

    private static final String SHADOW_SERVICE_URL                = "service_confifuration.shadow_service_url";
    private static final String CLOUD_URL                         = "service_configuration.cloud_url";
    private static final String CLOUD2_URL                        = "service_configuration.cloud2_url";
    private static final String FCM_SENDER_ID                     = "service_configuration.fcm_sender_id";
    private static final String STORAGE_URL                       = "service_configuration.storage_url";
    private static final String VOIP_URL                          = "service_configuration.voip_url";
    private static final String STATUS_URL                        = "service_configuration.status_url";
    private static final String UNIDENTIFIED_ACCESS_CA_PUBLIC_KEY = "service_configuration.unidentified_access_ca_public_key";
    private static final String ZK_PUBLIC_KEY                     = "service_configuration.zk_public_key";
    private static final String CURRENT_CERT_VERSION              = "service_configuration.current_cert_version";
    private static final String CURRENT_DIR_VERSION               = "service_configuration.current_dir_version";
    private static final String SUPPORT_EMAIL                     = "service_configuration.support_email";
    private static final String IS_LICENSED                       = "service_configuration.is_licensed";
    private static final String LICENSE                           = "service_configuration.license";
    private static final String IMAGE_MAX_SIZE                    = "service_configuration.maxsize_image";
    private static final String IMAGE_MAX_DIMENSION               = "service_configuration.maxdimen_image";
    private static final String GIF_MAX_SIZE                      = "service_configuration.maxsize_gif";
    private static final String AUDIO_MAX_SIZE                    = "service_configuration.maxsize_audio";
    private static final String VIDEO_MAX_SIZE                    = "service_configuration.maxsize_video";
    private static final String DOC_MAX_SIZE                      = "service_configuration.maxsize_doc";
    private static final String UPDATES_ALLOWED                   = "service_configuration.updates_allowed";
    private static final String PAYMENTS_ENABLED                   = "service_configuration.payments_enabled";

    public static final String EXAMPLE_URI                        = "https://example.com";

    // obsoleted
    private static final String TRIAL_STATUS                      = "service_configuration.trial_status";
    private static final String TRIAL_START_TIME                  = "service_configuration.trial_start_time";
    private static final String TRIAL_DURATION                    = "service_configuration.trial_duration";

    ServiceConfigurationValues(@NonNull KeyValueStore store) {
        super(store);
    }

    @Override
    void onFirstEverAppLaunch() {}

    @Override
    @NonNull
    List<String> getKeysToIncludeInBackup() {
        return Arrays.asList(CURRENT_CERT_VERSION,
                             CURRENT_DIR_VERSION,
                             LICENSE,
                             IS_LICENSED);
    }

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

    public synchronized void setVoipUrl(String voipUrl) {
        putString(VOIP_URL, voipUrl);
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

    public synchronized void storeLicense(byte [] license) {
        putBlob(LICENSE, license);
    }

    public synchronized void removeLicense() {
        getStore().beginWrite()
                  .remove(LICENSE)
                  .commit();
    }

    public synchronized void removeTrials() {
        getStore().beginWrite()
                .remove(TRIAL_STATUS)
                .remove(TRIAL_START_TIME)
                .remove(TRIAL_DURATION)
                .commit();
    }

    private void removeKey(String key) {
        getStore().beginWrite()
                .remove(key)
                .commit();
    }

    public synchronized void removeImageKey() {
        removeKey(IMAGE_MAX_SIZE);
    }

    public synchronized void removeImageDimenKey() {
        removeKey(IMAGE_MAX_DIMENSION);
    }

    public synchronized void removeGifKey() {
        removeKey(GIF_MAX_SIZE);
    }

    public synchronized void removeDocKey() {
        removeKey(DOC_MAX_SIZE);
    }

    public synchronized void removeAudioKey() {
        removeKey(AUDIO_MAX_SIZE);
    }

    public synchronized void removeVideoKey() {
        removeKey(VIDEO_MAX_SIZE);
    }

    public synchronized void setFcmSenderId(String senderId) {
        putString(FCM_SENDER_ID, senderId);
    }

    public synchronized void setImageMaxSize(int size) {
        putInteger(IMAGE_MAX_SIZE, size);
    }

    public synchronized void setImageMaxDimension(int dimension) {
        putInteger(IMAGE_MAX_DIMENSION, dimension);
    }

    public synchronized void setGifMaxSize(int size) {
        putInteger(GIF_MAX_SIZE, size);
    }

    public synchronized void setAudioMaxSize(int size) {
        putInteger(AUDIO_MAX_SIZE, size);
    }

    public synchronized void setVideoMaxSize(int size) {
        putInteger(VIDEO_MAX_SIZE, size);
    }

    public synchronized void setDocMaxSize(int size) {
        putInteger(DOC_MAX_SIZE, size);
    }

    public synchronized void setUpdatesAllowed(boolean allowed) {
        putBoolean(UPDATES_ALLOWED, allowed);
    }

    public synchronized void setPaymentsEnabled(boolean enabled) {
        putBoolean(PAYMENTS_ENABLED, enabled);
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

    public String getVoipUrl() {
        return getString(VOIP_URL, EXAMPLE_URI);
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

    public String getFcmSenderId() {
        return getString(FCM_SENDER_ID, "null");
    }

    // in MB
    public int getImageMaxSize() {
        return getInteger(IMAGE_MAX_SIZE, 6);
    }

    public int getImageMaxDimension() {
        return getInteger(IMAGE_MAX_DIMENSION, 4096);
    }

    // in MB
    public int getGifMaxSize() {
        return getInteger(GIF_MAX_SIZE, 25);
    }

    // in MB
    public int getAudioMaxSize() {
        return getInteger(AUDIO_MAX_SIZE, 100);
    }

    // in MB
    public int getVideoMaxSize() {
        return getInteger(VIDEO_MAX_SIZE, 100);
    }

    // in MB
    public int getDocMaxSize() {
        return getInteger(DOC_MAX_SIZE, 100);
    }

    public boolean getUpdatesAllowed() {
        return getBoolean(UPDATES_ALLOWED, true);
    }

    public boolean getPaymentsEnabled() {
        return getBoolean(PAYMENTS_ENABLED, false);
    }
}