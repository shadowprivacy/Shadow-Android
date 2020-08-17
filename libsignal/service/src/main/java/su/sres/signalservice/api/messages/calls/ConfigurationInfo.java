package su.sres.signalservice.api.messages.calls;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import su.sres.signalservice.internal.push.SenderCertificate;

public class ConfigurationInfo {

    @JsonProperty
    private String cloudUri;

    @JsonProperty
    private String statusUri;

    @JsonProperty
    private String storageUri;

    @JsonProperty
    @JsonDeserialize(using = SenderCertificate.ByteArrayDesieralizer.class)
    private byte[] unidentifiedDeliveryCaPublicKey;

    @JsonProperty
    @JsonDeserialize(using = SenderCertificate.ByteArrayDesieralizer.class)
    private byte[] zkPublicKey;

    @JsonProperty
    private String supportEmail;

    public String getCloudUri() {
        return cloudUri;
    }

    public String getStatusUri() {
        return statusUri;
    }

    public String getStorageUri() {
        return storageUri;
    }

    public byte[] getUnidentifiedDeliveryCaPublicKey() {
        return unidentifiedDeliveryCaPublicKey;
    }

    public byte[] getZkPublicKey() {
        return zkPublicKey;
    }

    public String getSupportEmail() {
        return supportEmail;
    }


}
