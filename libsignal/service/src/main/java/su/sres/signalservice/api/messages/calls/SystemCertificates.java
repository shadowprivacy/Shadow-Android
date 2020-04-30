package su.sres.signalservice.api.messages.calls;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import su.sres.signalservice.internal.push.SenderCertificate;

public class SystemCertificates {

    @JsonProperty
    @JsonDeserialize(using = SenderCertificate.ByteArrayDesieralizer.class)
    private byte[] cloudCert;


    public byte[] getSystemCertificate() {
        return cloudCert;
    }

    public void setCertificate(byte[] cert) {
        cloudCert = cert;
    }
}