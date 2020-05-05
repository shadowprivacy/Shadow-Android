package su.sres.securesms.notifications;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import su.sres.securesms.MainActivity;
import su.sres.securesms.R;
import su.sres.securesms.database.RecipientDatabase;
import su.sres.securesms.preferences.widgets.NotificationPrivacyPreference;
import su.sres.securesms.util.TextSecurePreferences;

public class PendingMessageNotificationBuilder extends AbstractNotificationBuilder {

  public PendingMessageNotificationBuilder(Context context, NotificationPrivacyPreference privacy) {
    super(context, privacy);

    // TODO [greyson] Navigation
    Intent intent = new Intent(context, MainActivity.class);

    setSmallIcon(R.drawable.ic_notification);
    setSmallIcon(R.drawable.ic_notification);
    setColor(context.getResources().getColor(R.color.core_ultramarine));

    setContentTitle(context.getString(R.string.MessageNotifier_you_may_have_new_messages));
    setContentText(context.getString(R.string.MessageNotifier_open_signal_to_check_for_recent_notifications));
    setTicker(context.getString(R.string.MessageNotifier_open_signal_to_check_for_recent_notifications));

    setContentIntent(PendingIntent.getActivity(context, 0, intent, 0));
    setAutoCancel(true);
    setAlarms(null, RecipientDatabase.VibrateState.DEFAULT);

    setOnlyAlertOnce(true);

    if (!NotificationChannels.supported()) {
      setPriority(TextSecurePreferences.getNotificationPriority(context));
    }
  }
}
