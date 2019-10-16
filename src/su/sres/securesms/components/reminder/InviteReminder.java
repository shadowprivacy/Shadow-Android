package su.sres.securesms.components.reminder;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;
import androidx.annotation.NonNull;
import android.view.View;
import android.view.View.OnClickListener;

import su.sres.securesms.R;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.util.concurrent.SignalExecutors;

@SuppressLint("StaticFieldLeak")
public class InviteReminder extends Reminder {

  public InviteReminder(final @NonNull Context context,
                        final @NonNull Recipient recipient)
  {
    super(context.getString(R.string.reminder_header_invite_title),
          context.getString(R.string.reminder_header_invite_text, recipient.toShortString()));

    setDismissListener(v -> SignalExecutors.BOUNDED.execute(() -> {
      DatabaseFactory.getRecipientDatabase(context).setSeenInviteReminder(recipient.getId(), true);
    }));
  }
}
