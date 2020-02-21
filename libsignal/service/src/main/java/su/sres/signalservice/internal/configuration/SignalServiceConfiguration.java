package su.sres.signalservice.internal.configuration;


public class SignalServiceConfiguration {

  private final SignalServiceUrl[] signalServiceUrls;
  private final SignalCdnUrl[]     signalCdnUrls;
  private final SignalStorageUrl[]          signalStorageUrls;


  public SignalServiceConfiguration(SignalServiceUrl[] signalServiceUrls, SignalCdnUrl[] signalCdnUrls, SignalStorageUrl[] signalStorageUrls) {
    this.signalServiceUrls  = signalServiceUrls;
    this.signalCdnUrls      = signalCdnUrls;
    this.signalStorageUrls  = signalStorageUrls;
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
}
