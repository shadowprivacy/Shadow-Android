package su.sres.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

public class WhoAmIResponse {
    @JsonProperty
    private String uuid;

    @JsonProperty
    private String pni;

    @JsonProperty
    private String userLogin;

    public String getAci() {
        return uuid;
    }

    public String getPni() {
        return pni;
    }

    public String getUserLogin() {
        return userLogin;
    }
}