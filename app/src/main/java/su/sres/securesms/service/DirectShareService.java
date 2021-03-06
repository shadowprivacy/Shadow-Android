package su.sres.securesms.service;

import android.content.ComponentName;
import android.content.Context;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.service.chooser.ChooserTarget;
import android.service.chooser.ChooserTargetService;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.view.ContextThemeWrapper;

import su.sres.securesms.R;
import su.sres.securesms.sharing.ShareActivity;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.ThreadDatabase;
import su.sres.securesms.database.model.ThreadRecord;
import su.sres.securesms.logging.Log;
import su.sres.securesms.mms.GlideApp;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.util.BitmapUtil;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;

@RequiresApi(api = Build.VERSION_CODES.M)
public class DirectShareService extends ChooserTargetService {

  private static final String TAG = DirectShareService.class.getSimpleName();

  @Override
  public List<ChooserTarget> onGetChooserTargets(ComponentName targetActivityName,
                                                 IntentFilter matchedFilter)
  {
    List<ChooserTarget> results        = new LinkedList<>();
    ComponentName       componentName  = new ComponentName(this, ShareActivity.class);
    ThreadDatabase      threadDatabase = DatabaseFactory.getThreadDatabase(this);
      Cursor              cursor         = threadDatabase.getRecentConversationList(10, false);

    try {
      ThreadDatabase.Reader reader = threadDatabase.readerFor(cursor);
      ThreadRecord record;

        while ((record = reader.getNext()) != null) {
          Recipient recipient = Recipient.resolved(record.getRecipient().getId());
          String    name      = recipient.getDisplayName(this);

          Bitmap avatar;

          if (recipient.getContactPhoto() != null) {
            try {
              avatar = GlideApp.with(this)
                               .asBitmap()
                               .load(recipient.getContactPhoto())
                               .circleCrop()
                               .submit(getResources().getDimensionPixelSize(android.R.dimen.notification_large_icon_width),
                                       getResources().getDimensionPixelSize(android.R.dimen.notification_large_icon_width))
                               .get();
            } catch (InterruptedException | ExecutionException e) {
              Log.w(TAG, e);
              avatar = getFallbackDrawable(recipient);
            }
          } else {
            avatar = getFallbackDrawable(recipient);
          }

          Bundle bundle = new Bundle();
          bundle.putLong(ShareActivity.EXTRA_THREAD_ID, record.getThreadId());
          bundle.putString(ShareActivity.EXTRA_RECIPIENT_ID, recipient.getId().serialize());
          bundle.putInt(ShareActivity.EXTRA_DISTRIBUTION_TYPE, record.getDistributionType());
          bundle.setClassLoader(getClassLoader());

          results.add(new ChooserTarget(name, Icon.createWithBitmap(avatar), 1.0f, componentName, bundle));
      }

      return results;
    } finally {
      if (cursor != null) cursor.close();
    }
  }

  private Bitmap getFallbackDrawable(@NonNull Recipient recipient) {
    Context themedContext = new ContextThemeWrapper(this, R.style.TextSecure_LightTheme);
    return BitmapUtil.createFromDrawable(recipient.getFallbackContactPhotoDrawable(themedContext, false),
                                         getResources().getDimensionPixelSize(android.R.dimen.notification_large_icon_width),
                                         getResources().getDimensionPixelSize(android.R.dimen.notification_large_icon_height));
  }
}
