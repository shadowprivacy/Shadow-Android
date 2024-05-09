package su.sres.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

public class WhoAmIResponse {
    @JsonProperty
    private String uuid;

    @JsonProperty
    private String userLogin;

    public String getUuid() {
        return uuid;
    }

    public String getUserLogin() {
        return userLogin;
    }
}