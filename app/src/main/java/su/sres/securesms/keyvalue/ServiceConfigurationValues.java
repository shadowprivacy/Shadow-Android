package su.sres.securesms.keyvalue;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class ServiceConfigurationValues {

    private static final String SHADOW_SERVICE_URL = "service_confifuration.shadow_service_url";
    private static final String CLOUD_URL = "service_configuration.cloud_url";
    private static final String CLOUD2_URL = "service_configuration.cloud2_url";
    private static final String STORAGE_URL = "service_configuration.storage_url";
    private static final String STATUS_URL = "service_configuration.status_url";
    private static final String UNIDENTIFIED_ACCESS_CA_PUBLIC_KEY = "service_configuration.unidentified_access_ca_public_key";
//    private static final String SERVER_CERT_PUBLIC_KEY = "service_configuration.server_cert_public_key";
    private static final String CURRENT_CERT_VERSION = "service_configuration.current_cert_version";
    private static final String SUPPORT_EMAIL        = "service_configuration.support_email";

    public static final String EXAMPLE_URI = "https://example.com";

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

    public synchronized void setCurrentCertVer(int certVer) {
        store.beginWrite()
                .putInteger(CURRENT_CERT_VERSION, certVer)
                .commit();
    }

    public synchronized void setSupportEmail(String supportEmail) {
        store.beginWrite()
                .putString(SUPPORT_EMAIL, supportEmail)
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

    public int getCurrentCertVer() {
        return store.getInteger(CURRENT_CERT_VERSION, 1);
    }

    public @Nullable
    String getSupportEmail() {
        return store.getString(SUPPORT_EMAIL, "example@example.com");
    }

}