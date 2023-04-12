package su.sres.securesms.push;

import android.content.Context;

import androidx.annotation.NonNull;

import su.sres.securesms.BuildConfig;
import su.sres.securesms.util.FeatureFlags;
import su.sres.signalservice.api.SignalServiceAccountManager;

import java.util.UUID;

public class AccountManagerFactory {

  private static final String TAG = AccountManagerFactory.class.getSimpleName();

  public static @NonNull SignalServiceAccountManager createAuthenticated(@NonNull Context context,
                                                                         @NonNull UUID uuid,
                                                                         @NonNull String userLogin,
                                                                         @NonNull String password)
  {

    return new SignalServiceAccountManager(new SignalServiceNetworkAccess(context).getConfiguration(),
            uuid, userLogin, password, BuildConfig.SIGNAL_AGENT, FeatureFlags.okHttpAutomaticRetry());
  }

  /**
   * Should only be used during registration when you haven't yet been assigned a UUID.
   */
  public static @NonNull SignalServiceAccountManager createUnauthenticated(@NonNull Context context,
                                                                           @NonNull String userLogin,
                                                                           @NonNull String password)
  {

    return new SignalServiceAccountManager(new SignalServiceNetworkAccess(context).getConfiguration(),
            null, userLogin, password, BuildConfig.SIGNAL_AGENT, FeatureFlags.okHttpAutomaticRetry());
  }

}
