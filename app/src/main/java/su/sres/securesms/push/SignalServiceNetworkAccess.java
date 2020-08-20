package su.sres.securesms.push;

import android.content.Context;

import androidx.annotation.Nullable;

import org.whispersystems.libsignal.util.guava.Optional;

import okhttp3.Dns;
import su.sres.securesms.BuildConfig;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.securesms.net.CustomDns;
import su.sres.securesms.net.SequentialDns;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Interceptor;

public class SignalServiceNetworkAccess {

    @SuppressWarnings("unused")
    private static final String TAG = SignalServiceNetworkAccess.class.getSimpleName();

    public static final Dns DNS = new SequentialDns(Dns.SYSTEM, new CustomDns("1.1.1.1"));

    private SignalServiceConfiguration Configuration;

    final List<Interceptor> interceptors = Collections.singletonList(new UserAgentInterceptor());
    final Optional<Dns>     dns          = Optional.of(DNS);

    public SignalServiceNetworkAccess(Context context) {


/**        this.Configuration = new SignalServiceConfiguration(new SignalServiceUrl[]{new SignalServiceUrl(SignalStore.serviceConfigurationValues().getShadowUrl(), new SignalServiceTrustStore(context))},
                new SignalCdnUrl[]{new SignalCdnUrl(SignalStore.serviceConfigurationValues().getCloudUrl(), new SignalServiceTrustStore(context))},
                new SignalStorageUrl[]{new SignalStorageUrl(SignalStore.serviceConfigurationValues().getStorageUrl(), new SignalServiceTrustStore(context))}, interceptors,
                zkGroupServerPublicParams); */

        renewConfiguration(context, interceptors, dns);

    }

    public SignalServiceConfiguration getConfiguration(Context context) {
        String localNumber = TextSecurePreferences.getLocalNumber(context);
        return getConfiguration(localNumber);
    }

    public SignalServiceConfiguration getConfiguration(@Nullable String localNumber) {
        return this.Configuration;
    }

    public void renewConfiguration(Context context, List<Interceptor> interceptors, Optional<Dns> dns) {
        this.Configuration = new SignalServiceConfiguration(new SignalServiceUrl[]{new SignalServiceUrl(SignalStore.serviceConfigurationValues().getShadowUrl(), new SignalServiceTrustStore(context))},
                makeSignalCdnUrlMapFor(new SignalCdnUrl[] {new SignalCdnUrl(SignalStore.serviceConfigurationValues().getCloudUrl(), new SignalServiceTrustStore(context))},
                                       new SignalCdnUrl[] {new SignalCdnUrl(SignalStore.serviceConfigurationValues().getCloud2Url(), new SignalServiceTrustStore(context))}),
                new SignalStorageUrl[]{new SignalStorageUrl(SignalStore.serviceConfigurationValues().getStorageUrl(), new SignalServiceTrustStore(context))}, interceptors,
                dns,
                SignalStore.serviceConfigurationValues().getZkPublicKey());
    }

    public void renewConfiguration(Context context) {
        renewConfiguration(context, interceptors, dns);
    }

    private static Map<Integer, SignalCdnUrl[]> makeSignalCdnUrlMapFor(SignalCdnUrl[] cdn0Urls, SignalCdnUrl[] cdn2Urls) {
        Map<Integer, SignalCdnUrl[]> result = new HashMap<>();
        result.put(0, cdn0Urls);
        result.put(2, cdn2Urls);
        return Collections.unmodifiableMap(result);
    }
}
