package su.sres.signalservice.internal.configuration;

import org.whispersystems.libsignal.util.guava.Optional;

import java.util.List;
import java.util.Map;

import okhttp3.Dns;
import okhttp3.Interceptor;

public final class SignalServiceConfiguration {

  private final SignalServiceUrl[] signalServiceUrls;
  private final Map<Integer, SignalCdnUrl[]> signalCdnUrlMap;
  private final SignalStorageUrl[] signalStorageUrls;
  private final List<Interceptor>  networkInterceptors;
  private final Optional<Dns>      dns;
  private final Optional<ShadowProxy>        proxy;
  private final byte[]             zkGroupServerPublicParams;


  public SignalServiceConfiguration(SignalServiceUrl[] signalServiceUrls,
                                    Map<Integer, SignalCdnUrl[]> signalCdnUrlMap,
                                    SignalStorageUrl[] signalStorageUrls,
                                    List<Interceptor> networkInterceptors,
                                    Optional<Dns> dns,
                                    Optional<ShadowProxy> proxy,
                                    byte[] zkGroupServerPublicParams)
  {
    this.signalServiceUrls          = signalServiceUrls;
    this.signalCdnUrlMap            = signalCdnUrlMap;
    this.signalStorageUrls          = signalStorageUrls;
    this.networkInterceptors        = networkInterceptors;
    this.dns                        = dns;
    this.proxy                      = proxy;
    this.zkGroupServerPublicParams  = zkGroupServerPublicParams;
  }

  public SignalServiceUrl[] getSignalServiceUrls() {
    return signalServiceUrls;
  }

  public Map<Integer, SignalCdnUrl[]> getSignalCdnUrlMap() {
    return signalCdnUrlMap;
  }

  public SignalStorageUrl[] getSignalStorageUrls() {
    return signalStorageUrls;
  }

  public List<Interceptor> getNetworkInterceptors() {
    return networkInterceptors;
  }

  public Optional<Dns> getDns() {
    return dns;
  }

  public byte[] getZkGroupServerPublicParams() {
    return zkGroupServerPublicParams;
  }

  public Optional<ShadowProxy> getShadowProxy() {
    return proxy;
  }
}
