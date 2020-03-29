package su.sres.securesms.push;

import android.content.Context;

import androidx.annotation.Nullable;

import su.sres.securesms.BuildConfig;
import su.sres.securesms.keyvalue.SignalStore;
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

    private SignalServiceConfiguration Configuration;

    final List<Interceptor> interceptors = Collections.singletonList(new UserAgentInterceptor());
    final byte[] zkGroupServerPublicParams;

    public SignalServiceNetworkAccess(Context context) {

//        final List<Interceptor> interceptors = Collections.singletonList(new UserAgentInterceptor());
//        final byte[] zkGroupServerPublicParams;

        try {
            zkGroupServerPublicParams = Base64.decode(BuildConfig.ZKGROUP_SERVER_PUBLIC_PARAMS);
        } catch (IOException e) {
            throw new AssertionError(e);

        }


/**        this.Configuration = new SignalServiceConfiguration(new SignalServiceUrl[]{new SignalServiceUrl(SignalStore.serviceConfigurationValues().getShadowUrl(), new SignalServiceTrustStore(context))},
                new SignalCdnUrl[]{new SignalCdnUrl(SignalStore.serviceConfigurationValues().getCloudUrl(), new SignalServiceTrustStore(context))},
                new SignalStorageUrl[]{new SignalStorageUrl(SignalStore.serviceConfigurationValues().getStorageUrl(), new SignalServiceTrustStore(context))}, interceptors,
                zkGroupServerPublicParams); */

        renewConfiguration(context, interceptors, zkGroupServerPublicParams);


    }

    public SignalServiceConfiguration getConfiguration(Context context) {
        String localNumber = TextSecurePreferences.getLocalNumber(context);
        return getConfiguration(localNumber);
    }

    public SignalServiceConfiguration getConfiguration(@Nullable String localNumber) {
        return this.Configuration;
    }

    public void renewConfiguration(Context context, List<Interceptor> interceptors, byte[] zkGroupServerPublicParams) {
        this.Configuration = new SignalServiceConfiguration(new SignalServiceUrl[]{new SignalServiceUrl(SignalStore.serviceConfigurationValues().getShadowUrl(), new SignalServiceTrustStore(context))},
                new SignalCdnUrl[]{new SignalCdnUrl(SignalStore.serviceConfigurationValues().getCloudUrl(), new SignalServiceTrustStore(context))},
                new SignalStorageUrl[]{new SignalStorageUrl(SignalStore.serviceConfigurationValues().getStorageUrl(), new SignalServiceTrustStore(context))}, interceptors,
                zkGroupServerPublicParams);
    }

    public void renewConfiguration(Context context) {
        renewConfiguration(context, interceptors, zkGroupServerPublicParams);
    }


}
