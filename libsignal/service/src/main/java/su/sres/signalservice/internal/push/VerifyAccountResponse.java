package su.sres.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

public class VerifyAccountResponse {
    @JsonProperty
    private String uuid;

    public String getUuid() {
        return uuid;
    }
}