package su.sres.securesms.jobmanager.impl;

import android.app.job.JobInfo;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import su.sres.securesms.jobmanager.Constraint;
import su.sres.securesms.util.NetworkUtil;
import su.sres.securesms.util.TextSecurePreferences;

import java.util.Collections;
import java.util.Set;

/**
 * Constraint for Emoji Downloads which respects users settings regarding image downloads and requires network.
 */
public class AutoDownloadEmojiConstraint implements Constraint {

    public static final String KEY = "AutoDownloadEmojiConstraint";

    private static final String IMAGE_TYPE = "image";

    private final Context context;

    private AutoDownloadEmojiConstraint(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public boolean isMet() {
        return canAutoDownloadEmoji(context);
    }

    @Override
    public @NonNull String getFactoryKey() {
        return KEY;
    }

    @RequiresApi(26)
    @Override
    public void applyToJobInfo(@NonNull JobInfo.Builder jobInfoBuilder) {
        boolean canDownloadWhileRoaming = TextSecurePreferences.getRoamingMediaDownloadAllowed(context).contains(IMAGE_TYPE);
        boolean canDownloadWhileMobile  = TextSecurePreferences.getMobileMediaDownloadAllowed(context).contains(IMAGE_TYPE);

        if (canDownloadWhileRoaming) {
            jobInfoBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
        } else if (canDownloadWhileMobile) {
            jobInfoBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_NOT_ROAMING);
        } else {
            jobInfoBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED);
        }
    }

    public static boolean canAutoDownloadEmoji(@NonNull Context context) {
        return getAllowedAutoDownloadTypes(context).contains(IMAGE_TYPE);
    }

    private static @NonNull Set<String> getAllowedAutoDownloadTypes(@NonNull Context context) {
        if      (NetworkUtil.isConnectedWifi(context))    return Collections.singleton(IMAGE_TYPE);
        else if (NetworkUtil.isConnectedRoaming(context)) return TextSecurePreferences.getRoamingMediaDownloadAllowed(context);
        else if (NetworkUtil.isConnectedMobile(context))  return TextSecurePreferences.getMobileMediaDownloadAllowed(context);
        else                                              return Collections.emptySet();
    }

    public static final class Factory implements Constraint.Factory<AutoDownloadEmojiConstraint> {

        private final Context context;

        public Factory(@NonNull Context context) {
            this.context = context.getApplicationContext();
        }

        @Override
        public AutoDownloadEmojiConstraint create() {
            return new AutoDownloadEmojiConstraint(context);
        }
    }
}
