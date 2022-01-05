package su.sres.securesms.net;

import android.os.Build;

import su.sres.securesms.BuildConfig;

/**
 * The user agent that should be used by default -- includes app name, version, etc.
 */
public class StandardUserAgentInterceptor extends UserAgentInterceptor {

    public StandardUserAgentInterceptor() {
        super("Signal-Android " + BuildConfig.VERSION_NAME + " (API " + Build.VERSION.SDK_INT + ")");
    }
}