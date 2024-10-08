package su.sres.signalservice.api.profiles;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class SignalServiceProfileWrite {

    @JsonProperty
    private String version;

    @JsonProperty
    private byte[] name;

    @JsonProperty
    private byte[] about;

    @JsonProperty
    private byte[] aboutEmoji;

    @JsonProperty
    private byte[] paymentAddress;

    @JsonProperty
    private boolean avatar;

    @JsonProperty
    private byte[] commitment;

    @JsonProperty
    private List<String> badgeIds;

    @JsonCreator
    public SignalServiceProfileWrite(){
    }

    public SignalServiceProfileWrite(String version, byte[] name, byte[] about, byte[] aboutEmoji, byte[] paymentAddress, boolean avatar, byte[] commitment, List<String> badgeIds) {
        this.version        = version;
        this.name           = name;
        this.about          = about;
        this.aboutEmoji     = aboutEmoji;
        this.paymentAddress = paymentAddress;
        this.avatar         = avatar;
        this.commitment     = commitment;
        this.badgeIds       = badgeIds;
    }

    public boolean hasAvatar() {
        return avatar;
    }

    public void setName(byte[] name) {
        this.name = name;
    }

    public void setCommitment(byte[] commitment) {
        this.commitment = commitment;
    }

    public byte[] getName() {
        return name;
    }

    public byte[] getCommitment() {
        return commitment;
    }
}