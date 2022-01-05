package su.sres.securesms.components.reminder;

import android.content.Context;

import su.sres.securesms.R;
import su.sres.securesms.util.PlayStoreUtil;
import su.sres.securesms.util.Util;

public class ExpiredBuildReminder extends Reminder {

  public ExpiredBuildReminder(final Context context) {
    super(context.getString(R.string.reminder_header_expired_build),
          context.getString(R.string.reminder_header_expired_build_details));
    setOkListener(v -> PlayStoreUtil.openPlayStoreOrOurApkDownloadPage(context));
  }

  @Override
  public boolean isDismissable() {
    return false;
  }

  public static boolean isEligible() {
    return Util.getDaysTillBuildExpiry() <= 0;
  }

}
