package su.sres.securesms.keyvalue;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceDataStore;

import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.logging.SignalUncaughtExceptionHandler;

/**
 * Simple, encrypted key-value store.
 */
public final class SignalStore {

    private static final SignalStore INSTANCE = new SignalStore();

    private final KeyValueStore              store;
    private final KbsValues                  kbsValues;
    private final RegistrationValues         registrationValues;
    private final RemoteConfigValues         remoteConfigValues;
    private final ServiceConfigurationValues serviceConfigValues;
    private final StorageServiceValues       storageServiceValues;
    private final UiHints                    uiHints;
    private final TooltipValues              tooltipValues;
    private final MiscellaneousValues        misc;
    private final InternalValues       internalValues;
    private final EmojiValues          emojiValues;
    private final NotificationSettings notificationSettings;

    private SignalStore() {
        this.store                = ApplicationDependencies.getKeyValueStore();
        this.kbsValues            = new KbsValues(store);
        this.registrationValues   = new RegistrationValues(store);
        this.remoteConfigValues   = new RemoteConfigValues(store);
        this.serviceConfigValues  = new ServiceConfigurationValues(store);
        this.storageServiceValues = new StorageServiceValues(store);
        this.uiHints              = new UiHints(store);
        this.tooltipValues        = new TooltipValues(store);
        this.misc                 = new MiscellaneousValues(store);
        this.internalValues       = new InternalValues(store);
        this.emojiValues          = new EmojiValues(store);
        this.notificationSettings = new NotificationSettings(store);
    }

    public static void onFirstEverAppLaunch() {
        kbsValues().onFirstEverAppLaunch();
        registrationValues().onFirstEverAppLaunch();
        remoteConfigValues().onFirstEverAppLaunch();
        serviceConfigurationValues().onFirstEverAppLaunch();
        storageServiceValues().onFirstEverAppLaunch();
        uiHints().onFirstEverAppLaunch();
        tooltips().onFirstEverAppLaunch();
        misc().onFirstEverAppLaunch();
        internalValues().onFirstEverAppLaunch();
        notificationSettings().onFirstEverAppLaunch();
    }

    public static @NonNull KbsValues kbsValues() {
        return INSTANCE.kbsValues;
    }

    public static @NonNull RegistrationValues registrationValues() {
        return INSTANCE.registrationValues;
    }

    public static @NonNull ServiceConfigurationValues serviceConfigurationValues() {
        return INSTANCE.serviceConfigValues;
    }

    public static @NonNull RemoteConfigValues remoteConfigValues() {
        return INSTANCE.remoteConfigValues;
    }

    public static @NonNull StorageServiceValues storageServiceValues() {
        return INSTANCE.storageServiceValues;
    }

    public static @NonNull UiHints uiHints() {
        return INSTANCE.uiHints;
    }

    public static @NonNull TooltipValues tooltips() {
        return INSTANCE.tooltipValues;
    }

    public static @NonNull MiscellaneousValues misc() {
        return INSTANCE.misc;
    }

    public static @NonNull InternalValues internalValues() {
        return INSTANCE.internalValues;
    }

    public static @NonNull EmojiValues emojiValues() {
        return INSTANCE.emojiValues;
    }

    public static @NonNull NotificationSettings notificationSettings() {
        return INSTANCE.notificationSettings;
    }

    public static @NonNull GroupsV2AuthorizationSignalStoreCache groupsV2AuthorizationCache() {
        return new GroupsV2AuthorizationSignalStoreCache(getStore());
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
        return INSTANCE.store;
    }
}