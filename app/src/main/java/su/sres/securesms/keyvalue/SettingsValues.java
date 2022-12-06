package su.sres.securesms.keyvalue;

import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class SettingsValues extends SignalStoreValues {

    public static final String LINK_PREVIEWS          = "settings.link_previews";
    public static final String KEEP_MESSAGES_DURATION = "settings.keep_messages_duration";

    private static final String SHADOW_BACKUP_DIRECTORY = "settings.shadow.backup.directory";

    public static final String THREAD_TRIM_LENGTH     = "pref_trim_length";
    public static final String THREAD_TRIM_ENABLED    = "pref_trim_threads";

    public static final String UPDATE_IN_ROAMING      = "settings.update_in_roaming";

    SettingsValues(@NonNull KeyValueStore store) {
        super(store);
    }

    @Override
    void onFirstEverAppLaunch() {
        getStore().beginWrite()
                .putBoolean(LINK_PREVIEWS, true)
                .apply();
    }

    public boolean isLinkPreviewsEnabled() {
        return getBoolean(LINK_PREVIEWS, false);
    }

    public void setLinkPreviewsEnabled(boolean enabled) {
        putBoolean(LINK_PREVIEWS, enabled);
    }

    public @NonNull KeepMessagesDuration getKeepMessagesDuration() {
        return KeepMessagesDuration.fromId(getInteger(KEEP_MESSAGES_DURATION, 0));
    }

    public void setKeepMessagesForDuration(@NonNull KeepMessagesDuration duration) {
        putInteger(KEEP_MESSAGES_DURATION, duration.getId());
    }

    public boolean isTrimByLengthEnabled() {
        return getBoolean(THREAD_TRIM_ENABLED, false);
    }

    public void setThreadTrimByLengthEnabled(boolean enabled) {
        putBoolean(THREAD_TRIM_ENABLED, enabled);
    }

    public int getThreadTrimLength() {
        return getInteger(THREAD_TRIM_LENGTH, 500);
    }

    public void setThreadTrimLength(int length) {
        putInteger(THREAD_TRIM_LENGTH, length);
    }

    public boolean isUpdateInRoamingEnabled() {
        return getBoolean(UPDATE_IN_ROAMING, true);
    }

    public void setUpdateInRoamingEnabled(boolean enabled) {
        putBoolean(UPDATE_IN_ROAMING, enabled);
    }

    public void setShadowBackupDirectory(@NonNull Uri uri) {
        putString(SHADOW_BACKUP_DIRECTORY, uri.toString());
    }

    public @Nullable
    Uri getSignalBackupDirectory() {
        String uri = getString(SHADOW_BACKUP_DIRECTORY, "");

        if (TextUtils.isEmpty(uri)) {
            return null;
        } else {
            return Uri.parse(uri);
        }
    }

    public void clearShadowBackupDirectory() {
        putString(SHADOW_BACKUP_DIRECTORY, null);
    }
}