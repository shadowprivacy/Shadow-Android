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
    private String voipUri;

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

    @JsonProperty
    private int maxImageSize;

    @JsonProperty
    private int maxImageDimension;

    @JsonProperty
    private int maxGifSize;

    @JsonProperty
    private int maxAudioSize;

    @JsonProperty
    private int maxVideoSize;

    @JsonProperty
    private int maxDocSize;

    @JsonProperty
    private boolean updatesAllowed;

    public String getCloudUri() {
        return cloudUri;
    }

    public String getStatusUri() {
        return statusUri;
    }

    public String getStorageUri() {
        return storageUri;
    }

    public String getVoipUri() {
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

    public Integer getMaxImageSize() {
        return maxImageSize;
    }

    public Integer getMaxImageDimension() {
        return maxImageDimension;
    }

    public Integer getMaxGifSize() {
        return maxGifSize;
    }

    public Integer getMaxAudioSize() {
        return maxAudioSize;
    }

    public Integer getMaxVideoSize() {
        return maxVideoSize;
    }

    public Integer getMaxDocSize() {
        return maxDocSize;
    }

    public boolean getUpdatesAllowed() {
        return updatesAllowed;
    }

    static class ByteArrayDeserializer extends JsonDeserializer<byte[]> {

        @Override
        public byte[] deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return Base64.decodeWithoutPadding(p.getValueAsString());
        }
    }
}
