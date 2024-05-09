package su.sres.signalservice.api.account;

import com.fasterxml.jackson.annotation.JsonProperty;

public final class ChangeUserLoginRequest {
  @JsonProperty
  private String userLogin;

  @JsonProperty
  private String code;

  @JsonProperty("reglock")
  private String registrationLock;

  public ChangeUserLoginRequest(String userLogin, String code) {
    this.userLogin = userLogin;
    this.code      = code;
  }

  public String getUserLogin() {
    return userLogin;
  }

  public String getCode() {
    return code;
  }
}