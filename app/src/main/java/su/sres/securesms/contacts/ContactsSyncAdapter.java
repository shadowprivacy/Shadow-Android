package su.sres.securesms.contacts;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.SyncResult;
import android.os.Bundle;
import su.sres.core.util.logging.Log;

import su.sres.securesms.contacts.sync.DirectoryHelper;
import su.sres.securesms.util.TextSecurePreferences;

import java.io.IOException;

public class ContactsSyncAdapter extends AbstractThreadedSyncAdapter {

  private static final String TAG = Log.tag(ContactsSyncAdapter.class);

  public ContactsSyncAdapter(Context context, boolean autoInitialize) {
    super(context, autoInitialize);
  }

  @Override
  public void onPerformSync(Account account, Bundle extras, String authority,
                            ContentProviderClient provider, SyncResult syncResult)
  {
    Log.i(TAG, "onPerformSync(" + authority +")");

    if (TextSecurePreferences.isPushRegistered(getContext())) {
      try {
        DirectoryHelper.refreshDirectory(getContext());
      } catch (IOException e) {
        Log.w(TAG, e);
      }
    }
  }

  @Override
  public void onSyncCanceled() {
    Log.w(TAG, "onSyncCanceled()");
  }

  @Override
  public void onSyncCanceled(Thread thread) {
    Log.w(TAG, "onSyncCanceled(" + thread + ")");
  }

}
