package su.sres.signalservice.internal.configuration;

import java.util.List;

import okhttp3.Interceptor;

public class SignalServiceConfiguration {

  private final SignalServiceUrl[] signalServiceUrls;
  private final SignalCdnUrl[]     signalCdnUrls;
  private final SignalStorageUrl[] signalStorageUrls;
  private final List<Interceptor>           networkInterceptors;


  public SignalServiceConfiguration(SignalServiceUrl[] signalServiceUrls, SignalCdnUrl[] signalCdnUrls, SignalStorageUrl[] signalStorageUrls,
                                    List<Interceptor> networkInterceptors) {
    this.signalServiceUrls  = signalServiceUrls;
    this.signalCdnUrls      = signalCdnUrls;
    this.signalStorageUrls  = signalStorageUrls;
    this.networkInterceptors        = networkInterceptors;
  }

  public SignalServiceUrl[] getSignalServiceUrls() {
    return signalServiceUrls;
  }

  public SignalCdnUrl[] getSignalCdnUrls() {
    return signalCdnUrls;
  }

  public SignalStorageUrl[] getSignalStorageUrls() {
    return signalStorageUrls;
  }

  public List<Interceptor> getNetworkInterceptors() {
    return networkInterceptors;
  }
}
