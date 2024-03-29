package su.sres.signalservice.api.messages.multidevice;

import su.sres.signalservice.api.push.SignalServiceAddress;

public class ViewedMessage {

    private final SignalServiceAddress sender;
    private final long                 timestamp;

    public ViewedMessage(SignalServiceAddress sender, long timestamp) {
        this.sender    = sender;
        this.timestamp = timestamp;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public SignalServiceAddress getSender() {
        return sender;
    }
}
