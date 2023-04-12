package su.sres.signalservice.internal.configuration;

public class ShadowProxy {
    private final String host;
    private final int    port;

    public ShadowProxy(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }
}
