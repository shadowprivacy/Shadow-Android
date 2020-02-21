package su.sres.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AuthorizationToken {

  @JsonProperty
  private String token;

  public String getToken() {
    return token;
  }
}
