package su.sres.securesms.push;

import android.content.Context;
import androidx.annotation.Nullable;

import su.sres.securesms.BuildConfig;
import su.sres.securesms.net.UserAgentInterceptor;
// import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.util.Base64;
import su.sres.securesms.util.TextSecurePreferences;
import su.sres.signalservice.internal.configuration.SignalCdnUrl;
import su.sres.signalservice.internal.configuration.SignalServiceConfiguration;
import su.sres.signalservice.internal.configuration.SignalServiceUrl;
import su.sres.signalservice.internal.configuration.SignalStorageUrl;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import okhttp3.Interceptor;

public class SignalServiceNetworkAccess {

  @SuppressWarnings("unused")
  private static final String TAG = SignalServiceNetworkAccess.class.getSimpleName();

  private final SignalServiceConfiguration              Configuration;

  public SignalServiceNetworkAccess(Context context) {

    final List<Interceptor> interceptors = Collections.singletonList(new UserAgentInterceptor());
    final byte[] zkGroupServerPublicParams;

    try {
      zkGroupServerPublicParams = Base64.decode(BuildConfig.ZKGROUP_SERVER_PUBLIC_PARAMS);
    } catch (IOException e) {
      throw new AssertionError(e);

    }

    // taking the server URL from the config database
//    this.uncensoredConfiguration = new SignalServiceConfiguration(new SignalServiceUrl[] {new SignalServiceUrl(DatabaseFactory.getConfigDatabase(context).getConfigById(1), new SignalServiceTrustStore(context))},
    this.Configuration = new SignalServiceConfiguration(new SignalServiceUrl[] {new SignalServiceUrl(TextSecurePreferences.getShadowServerUrl(context), new SignalServiceTrustStore(context))},
                                                                  new SignalCdnUrl[] {new SignalCdnUrl(TextSecurePreferences.getCloudUrl(context), new SignalServiceTrustStore(context))},
            new SignalStorageUrl[] {new SignalStorageUrl(TextSecurePreferences.getStorageUrl(context), new SignalServiceTrustStore(context))}, interceptors,
            zkGroupServerPublicParams);

  }

  public SignalServiceConfiguration getConfiguration(Context context) {
    String localNumber = TextSecurePreferences.getLocalNumber(context);
    return getConfiguration(localNumber);
  }

  public SignalServiceConfiguration getConfiguration(@Nullable String localNumber) {
 //    if (localNumber == null) return this.uncensoredConfiguration;
     return this.Configuration;
  }

/*  public boolean isCensored(Context context) {
    return getConfiguration(context) != this.uncensoredConfiguration;
  }

  public boolean isCensored(String number) {
    return getConfiguration(number) != this.uncensoredConfiguration;
  }

 */

}
