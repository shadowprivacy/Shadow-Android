package su.sres.securesms.push;

import android.content.Context;

import androidx.annotation.NonNull;

import su.sres.securesms.BuildConfig;
import su.sres.signalservice.api.SignalServiceAccountManager;

import java.util.UUID;

public class AccountManagerFactory {

  private static final String TAG = AccountManagerFactory.class.getSimpleName();

  public static @NonNull SignalServiceAccountManager createAuthenticated(@NonNull Context context,
                                                                         @NonNull UUID uuid,
                                                                         @NonNull String number,
                                                                         @NonNull String password)
  {

    return new SignalServiceAccountManager(new SignalServiceNetworkAccess(context).getConfiguration(number),
            uuid, number, password, BuildConfig.SIGNAL_AGENT);
  }

  /**
   * Should only be used during registration when you haven't yet been assigned a UUID.
   */
  public static @NonNull SignalServiceAccountManager createUnauthenticated(@NonNull Context context,
                                                                           @NonNull String number,
                                                                           @NonNull String password)
  {

    return new SignalServiceAccountManager(new SignalServiceNetworkAccess(context).getConfiguration(number),
            null, number, password, BuildConfig.SIGNAL_AGENT);
  }

}
