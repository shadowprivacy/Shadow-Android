package su.sres.securesms.components.reminder;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import androidx.annotation.NonNull;
import android.view.View;
import android.view.View.OnClickListener;

import su.sres.securesms.InviteActivity;
import su.sres.securesms.R;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.util.TextSecurePreferences;

public class ShareReminder extends Reminder {

  public ShareReminder(final @NonNull Context context) {
    super(context.getString(R.string.reminder_header_share_title),
          context.getString(R.string.reminder_header_share_text));

    setDismissListener(new OnClickListener() {
      @Override public void onClick(View v) {
        TextSecurePreferences.setPromptedShare(context, true);
      }
    });

    setOkListener(new OnClickListener() {
      @Override public void onClick(View v) {
        TextSecurePreferences.setPromptedShare(context, true);
        context.startActivity(new Intent(context, InviteActivity.class));
      }
    });
  }

  public static boolean isEligible(final @NonNull Context context) {
    if (!TextSecurePreferences.isPushRegistered(context) ||
        TextSecurePreferences.hasPromptedShare(context))
    {
      return false;
    }

    Cursor cursor = null;
    try {
      cursor = DatabaseFactory.getThreadDatabase(context).getConversationList();
      return cursor.getCount() >= 1;
    } finally {
      if (cursor != null) cursor.close();
    }
  }
}
