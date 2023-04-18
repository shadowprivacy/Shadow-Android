package su.sres.securesms.util.dynamiclanguage;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import androidx.annotation.NonNull;

import java.util.Locale;

import su.sres.securesms.util.TextSecurePreferences;

/**
 * Updates a context with an alternative language.
 */
public final class DynamicLanguageContextWrapper {
    private DynamicLanguageContextWrapper() {}

    public static void prepareOverrideConfiguration(@NonNull Context context, @NonNull Configuration base) {
        Locale newLocale = getUsersSelectedLocale(context);

        Locale.setDefault(newLocale);

        base.setLocale(newLocale);
    }

    public static @NonNull Locale getUsersSelectedLocale(@NonNull Context context) {
        String language  = TextSecurePreferences.getLanguage(context);
        return LocaleParser.findBestMatchingLocaleForLanguage(language);
    }

    public static void updateContext(@NonNull Context base) {
        Configuration config = base.getResources().getConfiguration();

        prepareOverrideConfiguration(base, config);
    }
}