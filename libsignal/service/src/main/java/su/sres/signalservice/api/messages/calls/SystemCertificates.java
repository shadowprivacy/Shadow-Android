package su.sres.signalservice.api.messages.calls;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import su.sres.signalservice.internal.push.SenderCertificate;

public class SystemCertificates {

    @JsonProperty
    @JsonDeserialize(using = SenderCertificate.ByteArrayDesieralizer.class)
    private byte[] cloudCertA;

    @JsonProperty
    @JsonDeserialize(using = SenderCertificate.ByteArrayDesieralizer.class)
    private byte[] cloudCertB;

    @JsonProperty
    @JsonDeserialize(using = SenderCertificate.ByteArrayDesieralizer.class)
    private byte[] shadowCertA;

    @JsonProperty
    @JsonDeserialize(using = SenderCertificate.ByteArrayDesieralizer.class)
    private byte[] shadowCertB;

    @JsonProperty
    @JsonDeserialize(using = SenderCertificate.ByteArrayDesieralizer.class)
    private byte[] storageCertA;

    @JsonProperty
    @JsonDeserialize(using = SenderCertificate.ByteArrayDesieralizer.class)
    private byte[] storageCertB;

    @JsonProperty
    @JsonDeserialize(using = SenderCertificate.ByteArrayDesieralizer.class)
    private byte[] sfuCertA;

    @JsonProperty
    @JsonDeserialize(using = SenderCertificate.ByteArrayDesieralizer.class)
    private byte[] sfuCertB;


    public byte[] getCloudCertA() {
        return cloudCertA;
    }

    public byte[] getCloudCertB() {
        return cloudCertB;
    }

    public byte[] getShadowCertA() {
        return shadowCertA;
    }

    public byte[] getShadowCertB() {
        return shadowCertB;
    }

    public byte[] getSfuCertA() {
        return sfuCertA;
    }

    public byte[] getSfuCertB() {
        return sfuCertB;
    }

    public byte[] getStorageCertA() {
        return storageCertA;
    }

    public byte[] getStorageCertB() {
        return storageCertB;
    }

    public void setCloudCertA(byte[] cert) {
        cloudCertA = cert;
    }

    public void setCloudCertB(byte[] cert) {
        cloudCertB = cert;
    }

    public void setShadowCertA(byte[] cert) {
        shadowCertA = cert;
    }

    public void setShadowCertB(byte[] cert) {
        shadowCertB = cert;
    }

    public void setSfuCertA(byte[] cert) {
        sfuCertA = cert;
    }

    public void setSfuCertB(byte[] cert) {
        sfuCertB = cert;
    }

    public void setStorageCertA(byte[] cert) {
        storageCertA = cert;
    }

    public void setStorageCertB(byte[] cert) {
        storageCertB = cert;
    }
}