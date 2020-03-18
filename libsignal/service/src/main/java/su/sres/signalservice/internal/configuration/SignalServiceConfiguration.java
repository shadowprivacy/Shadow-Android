package su.sres.signalservice.internal.configuration;

import java.util.List;

import okhttp3.Interceptor;

public final class SignalServiceConfiguration {

  private final SignalServiceUrl[] signalServiceUrls;
  private final SignalCdnUrl[]     signalCdnUrls;
  private final SignalStorageUrl[] signalStorageUrls;
  private final List<Interceptor>           networkInterceptors;
  private final byte[]                      zkGroupServerPublicParams;


  public SignalServiceConfiguration(SignalServiceUrl[] signalServiceUrls, SignalCdnUrl[] signalCdnUrls, SignalStorageUrl[] signalStorageUrls,
                                    List<Interceptor> networkInterceptors,
                                    byte[] zkGroupServerPublicParams)
  {
    this.signalServiceUrls  = signalServiceUrls;
    this.signalCdnUrls      = signalCdnUrls;
    this.signalStorageUrls  = signalStorageUrls;
    this.networkInterceptors        = networkInterceptors;
    this.zkGroupServerPublicParams  = zkGroupServerPublicParams;
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

  public byte[] getZkGroupServerPublicParams() {
    return zkGroupServerPublicParams;
  }
}
