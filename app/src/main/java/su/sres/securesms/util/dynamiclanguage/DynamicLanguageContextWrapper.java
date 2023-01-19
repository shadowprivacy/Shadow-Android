package su.sres.securesms.util.dynamiclanguage;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import java.util.Locale;

import su.sres.securesms.util.TextSecurePreferences;

/**
 * Updates a context with an alternative language.
 */
public final class DynamicLanguageContextWrapper {
    private DynamicLanguageContextWrapper() {}

    public static void prepareOverrideConfiguration(Context context, Configuration base) {
        String language  = TextSecurePreferences.getLanguage(context);
        Locale newLocale = LocaleParser.findBestMatchingLocaleForLanguage(language);

        Locale.setDefault(newLocale);

        base.setLocale(newLocale);
    }

    public static void updateContext(Context base) {
        Configuration config = base.getResources().getConfiguration();

        prepareOverrideConfiguration(base, config);
    }
}