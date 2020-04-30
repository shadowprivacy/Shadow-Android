package su.sres.securesms.events;

public class ServerCertErrorEvent {
    public final int message;

    public ServerCertErrorEvent(int message) {
        this.message = message;
    }
}
