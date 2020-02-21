package su.sres.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

public class WhoAmIResponse {
    @JsonProperty
    private String uuid;

    public String getUuid() {
        return uuid;
    }
}