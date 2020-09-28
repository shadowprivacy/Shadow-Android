package su.sres.signalservice.api.messages.calls;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.io.IOException;

import su.sres.signalservice.internal.push.SenderCertificate;
import su.sres.util.Base64;

public class ConfigurationInfo {

    @JsonProperty
    private String cloudUri;

    @JsonProperty
    private String statusUri;

    @JsonProperty
    private String storageUri;

    @JsonProperty
    @JsonDeserialize(using = ByteArrayDeserializer.class)
    private byte[] unidentifiedDeliveryCaPublicKey;

    @JsonProperty
    @JsonDeserialize(using = ByteArrayDeserializer.class)
    private byte[] serverZkPublic;

    @JsonProperty
    private String supportEmail;

    @JsonProperty
    private String fcmSenderId;

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
        return serverZkPublic;
    }

    public String getSupportEmail() {
        return supportEmail;
    }

    public String getFcmSenderId() {
        return fcmSenderId;
    }

    static class ByteArrayDeserializer extends JsonDeserializer<byte[]> {

        @Override
        public byte[] deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return Base64.decodeWithoutPadding(p.getValueAsString());
        }
    }


}
