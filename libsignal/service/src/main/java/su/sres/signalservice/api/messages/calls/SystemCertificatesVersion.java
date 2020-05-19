package su.sres.signalservice.api.messages.calls;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SystemCertificatesVersion {

    @JsonProperty
    private int certsVersion;


    public int getCertsVersion() {
        return certsVersion;
    }

    public void setCertsVersion(int v) {
        certsVersion = v;
    }
}