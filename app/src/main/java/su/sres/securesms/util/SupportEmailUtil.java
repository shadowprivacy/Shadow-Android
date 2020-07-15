package su.sres.securesms.util;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import su.sres.securesms.BuildConfig;
import su.sres.securesms.R;
import su.sres.securesms.keyvalue.SignalStore;

import java.util.Locale;

public final class SupportEmailUtil {

    private SupportEmailUtil() { }

    public static @NonNull String getSupportEmailAddress() {
        return SignalStore.serviceConfigurationValues().getSupportEmail();
    }

    /**
     * Generates a support email body with system info near the top.
     */
    public static @NonNull String generateSupportEmailBody(@NonNull Context context,
                                                           @NonNull String subject,
                                                           @Nullable String prefix,
                                                           @Nullable String suffix)
    {
        prefix = Util.firstNonNull(prefix, "");
        suffix = Util.firstNonNull(suffix, "");
        return String.format("%s\n%s\n%s", prefix, buildSystemInfo(context, subject), suffix);
    }

    private static @NonNull String buildSystemInfo(@NonNull Context context, @NonNull String subject) {
        return "--- " + context.getString(R.string.HelpFragment__support_info) + " ---" +
                "\n" +
                context.getString(R.string.SupportEmailUtil_subject) + " " + subject +
                "\n" +
                context.getString(R.string.SupportEmailUtil_device_info) + " " + getDeviceInfo() +
                "\n" +
                context.getString(R.string.SupportEmailUtil_android_version) + " " + getAndroidVersion() +
                "\n" +
                context.getString(R.string.SupportEmailUtil_signal_version) + " " + getSignalVersion() +
                "\n" +
                context.getString(R.string.SupportEmailUtil_signal_package) + " " + getSignalPackage(context) +
                "\n" +
                context.getString(R.string.SupportEmailUtil_locale) + " " + Locale.getDefault().toString();
    }

    private static CharSequence getDeviceInfo() {
        return String.format("%s %s (%s)", Build.MANUFACTURER, Build.MODEL, Build.PRODUCT);
    }

    private static CharSequence getAndroidVersion() {
        return String.format("%s (%s, %s)", Build.VERSION.RELEASE, Build.VERSION.INCREMENTAL, Build.DISPLAY);
    }

    private static CharSequence getSignalVersion() {
        return BuildConfig.VERSION_NAME;
    }

    private static CharSequence getSignalPackage(@NonNull Context context) {
        return String.format("%s (%s)", BuildConfig.APPLICATION_ID, AppSignatureUtil.getAppSignature(context).or("Unknown"));
    }
}