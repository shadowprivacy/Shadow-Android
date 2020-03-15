package su.sres.securesms.push;

import android.content.Context;
import androidx.annotation.Nullable;

import su.sres.securesms.BuildConfig;
import su.sres.securesms.net.UserAgentInterceptor;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.util.TextSecurePreferences;
import su.sres.signalservice.api.push.TrustStore;
import su.sres.signalservice.internal.configuration.SignalCdnUrl;
import su.sres.signalservice.internal.configuration.SignalServiceConfiguration;
import su.sres.signalservice.internal.configuration.SignalServiceUrl;
import su.sres.signalservice.internal.configuration.SignalStorageUrl;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.CipherSuite;
import okhttp3.ConnectionSpec;
import okhttp3.Interceptor;
import okhttp3.TlsVersion;

public class SignalServiceNetworkAccess {

  @SuppressWarnings("unused")
  private static final String TAG = SignalServiceNetworkAccess.class.getSimpleName();

  private final SignalServiceConfiguration              Configuration;

  public SignalServiceNetworkAccess(Context context) {

    final List<Interceptor> interceptors = Collections.singletonList(new UserAgentInterceptor());

    // taking the server URL from the config database
//    this.uncensoredConfiguration = new SignalServiceConfiguration(new SignalServiceUrl[] {new SignalServiceUrl(DatabaseFactory.getConfigDatabase(context).getConfigById(1), new SignalServiceTrustStore(context))},
    this.Configuration = new SignalServiceConfiguration(new SignalServiceUrl[] {new SignalServiceUrl(TextSecurePreferences.getShadowServerUrl(context), new SignalServiceTrustStore(context))},
                                                                  new SignalCdnUrl[] {new SignalCdnUrl(TextSecurePreferences.getCloudUrl(context), new SignalServiceTrustStore(context))},
            new SignalStorageUrl[] {new SignalStorageUrl(TextSecurePreferences.getStorageUrl(context), new SignalServiceTrustStore(context))}, interceptors);

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
