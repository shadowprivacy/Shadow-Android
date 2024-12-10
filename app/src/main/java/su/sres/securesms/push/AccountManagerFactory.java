package su.sres.securesms.push;

import android.content.Context;

import androidx.annotation.NonNull;

import su.sres.core.util.logging.Log;
import su.sres.securesms.BuildConfig;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.util.FeatureFlags;
import su.sres.signalservice.api.SignalServiceAccountManager;
import su.sres.signalservice.api.push.ACI;

import java.util.UUID;

public class AccountManagerFactory {

  private static final String TAG = Log.tag(AccountManagerFactory.class);

  public static @NonNull SignalServiceAccountManager createAuthenticated(@NonNull Context context,
                                                                         @NonNull ACI aci,
                                                                         @NonNull String userLogin,
                                                                         @NonNull String password)
  {

    return new SignalServiceAccountManager(ApplicationDependencies.getSignalServiceNetworkAccess().getConfiguration(),
                                           aci, userLogin, password, BuildConfig.SIGNAL_AGENT, FeatureFlags.okHttpAutomaticRetry());
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
