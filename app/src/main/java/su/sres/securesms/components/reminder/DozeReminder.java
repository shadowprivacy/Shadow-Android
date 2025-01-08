package su.sres.securesms.components.reminder;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;
import androidx.annotation.NonNull;

import su.sres.securesms.R;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.securesms.util.TextSecurePreferences;

@SuppressLint("BatteryLife")
public class DozeReminder extends Reminder {

  public DozeReminder(@NonNull final Context context) {
    super(context.getString(R.string.DozeReminder_optimize_for_missing_play_services),
          context.getString(R.string.DozeReminder_this_device_does_not_support_play_services_tap_to_disable_system_battery));

    setOkListener(v -> {
      TextSecurePreferences.setPromptedOptimizeDoze(context, true);
      Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                 Uri.parse("package:" + context.getPackageName()));
      context.startActivity(intent);
    });

    setDismissListener(v -> TextSecurePreferences.setPromptedOptimizeDoze(context, true));
  }

  public static boolean isEligible(Context context) {
    return !SignalStore.account().isFcmEnabled() && !TextSecurePreferences.hasPromptedOptimizeDoze(context) && !((PowerManager) context.getSystemService(Context.POWER_SERVICE)).isIgnoringBatteryOptimizations(context.getPackageName());
  }

}
