package su.sres.securesms.keyvalue;

import androidx.annotation.NonNull;

import su.sres.securesms.util.FeatureFlags;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class UserLoginPrivacyValues extends SignalStoreValues {

    public static final String SHARING_MODE = "userLoginPrivacy.sharingMode";
    public static final String LISTING_MODE = "userLoginPrivacy.listingMode";

    private static final Collection<CertificateType> REGULAR_CERTIFICATE = Collections.singletonList(CertificateType.UUID_AND_E164);
    private static final Collection<CertificateType> PRIVACY_CERTIFICATE = Collections.singletonList(CertificateType.UUID_ONLY);
    private static final Collection<CertificateType> BOTH_CERTIFICATES   = Collections.unmodifiableCollection(Arrays.asList(CertificateType.UUID_AND_E164, CertificateType.UUID_ONLY));

    UserLoginPrivacyValues(@NonNull KeyValueStore store) {
        super(store);
    }

    @Override
    void onFirstEverAppLaunch() {
        // TODO [ALAN] UserLoginPrivacy: During registration, set the attribute to so that new registrations start out as not listed
        //getStore().beginWrite()
        //          .putInteger(LISTING_MODE, UserLoginListingMode.UNLISTED.ordinal())
        //          .apply();
    }

    @Override
    @NonNull
    List<String> getKeysToIncludeInBackup() {
        return Arrays.asList(SHARING_MODE, LISTING_MODE);
    }

    public @NonNull UserLoginSharingMode getUserLoginSharingMode() {
        if (!FeatureFlags.UserLoginPrivacy()) return UserLoginSharingMode.EVERYONE;
        return UserLoginSharingMode.values()[getInteger(SHARING_MODE, UserLoginSharingMode.EVERYONE.ordinal())];
    }

    public void setUserLoginSharingMode(@NonNull UserLoginSharingMode UserLoginSharingMode) {
        putInteger(SHARING_MODE, UserLoginSharingMode.ordinal());
    }

    public @NonNull UserLoginListingMode getUserLoginListingMode() {
        if (!FeatureFlags.UserLoginPrivacy()) return UserLoginListingMode.LISTED;
        return UserLoginListingMode.values()[getInteger(LISTING_MODE, UserLoginListingMode.LISTED.ordinal())];
    }

    public void setUserLoginListingMode(@NonNull UserLoginListingMode UserLoginListingMode) {
        putInteger(LISTING_MODE, UserLoginListingMode.ordinal());
    }

    /**
     * If you respect {@link #getUserLoginSharingMode}, then you will only ever need to fetch and store
     * these certificates types.
     */
    public Collection<CertificateType> getRequiredCertificateTypes() {
        switch (getUserLoginSharingMode()) {
            case EVERYONE: return REGULAR_CERTIFICATE;
            case NOBODY  : return PRIVACY_CERTIFICATE;
            default      : throw new AssertionError();
        }
    }

    /**
     * All certificate types required according to the feature flags.
     */
    public Collection<CertificateType> getAllCertificateTypes() {
        return FeatureFlags.UserLoginPrivacy() ? BOTH_CERTIFICATES : REGULAR_CERTIFICATE;
    }

    /**
     * Serialized, do not change ordinal/order
     */
    public enum UserLoginSharingMode {
        EVERYONE,
        NOBODY
    }

    /**
     * Serialized, do not change ordinal/order
     */
    public enum UserLoginListingMode {
        LISTED,
        UNLISTED;

        public boolean isDiscoverable() {
            return this == LISTED;
        }

        public boolean isUnlisted() {
            return this == UNLISTED;
        }
    }
}
