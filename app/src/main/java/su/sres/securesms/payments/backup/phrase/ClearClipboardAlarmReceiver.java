package su.sres.securesms.payments.backup.phrase;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;

import su.sres.core.util.logging.Log;
import su.sres.securesms.R;
import su.sres.securesms.util.ServiceUtil;

public class ClearClipboardAlarmReceiver extends BroadcastReceiver {
  private static final String TAG = Log.tag(ClearClipboardAlarmReceiver.class);

  @Override
  public void onReceive(Context context, Intent intent) {
    Log.d(TAG, "onReceive: clearing clipboard");

    ClipboardManager clipboardManager = ServiceUtil.getClipboardManager(context);
    clipboardManager.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.app_name), " "));
  }
}
