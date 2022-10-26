/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package su.sres.signalservice.api.account;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import su.sres.signalservice.api.profiles.SignalServiceProfile;

public class AccountAttributes {

  @JsonProperty
  private String  signalingKey;

  @JsonProperty
  private int     registrationId;

  @JsonProperty
  private boolean voice;

  @JsonProperty
  private boolean video;

  @JsonProperty
  private boolean fetchesMessages;

  @JsonProperty
  private String pin;

  @JsonProperty
  private byte[] unidentifiedAccessKey;

  @JsonProperty
  private boolean unrestrictedUnidentifiedAccess;

  @JsonProperty
  private boolean discoverableByUserLogin;

  @JsonProperty
  private Capabilities capabilities;

  public AccountAttributes(String signalingKey,
                           int registrationId,
                           boolean fetchesMessages,
                           String pin,
                           byte[] unidentifiedAccessKey,
                           boolean unrestrictedUnidentifiedAccess,
                           Capabilities capabilities,
                           boolean discoverableByUserLogin)
  {
    this.signalingKey                   = signalingKey;
    this.registrationId                 = registrationId;
    this.voice                          = true;
    this.video                          = true;
    this.fetchesMessages                = fetchesMessages;
    this.pin                            = pin;
    this.unidentifiedAccessKey          = unidentifiedAccessKey;
    this.unrestrictedUnidentifiedAccess = unrestrictedUnidentifiedAccess;
    this.capabilities                   = capabilities;
    this.discoverableByUserLogin        = discoverableByUserLogin;
  }

  public AccountAttributes() {}

  public String getSignalingKey() {
    return signalingKey;
  }

  public int getRegistrationId() {
    return registrationId;
  }

  public boolean isVoice() {
    return voice;
  }

  public boolean isVideo() {
    return video;
  }

  public boolean isFetchesMessages() {
    return fetchesMessages;
  }

  public String getPin() {
    return pin;
  }

  public byte[] getUnidentifiedAccessKey() {
    return unidentifiedAccessKey;
  }

  public boolean isUnrestrictedUnidentifiedAccess() {
    return unrestrictedUnidentifiedAccess;
  }

  public boolean isDiscoverableByUserLogin() {
    return discoverableByUserLogin;
  }

  public Capabilities getCapabilities() {
    return capabilities;
  }

  public static class Capabilities {
    @JsonProperty
    private boolean uuid;

    @JsonProperty("gv2-3")
    private boolean gv2;

    @JsonProperty
    private boolean storage;

    @JsonCreator
    public Capabilities() {}

    public Capabilities(boolean uuid, boolean gv2, boolean storage) {
      this.uuid    = uuid;
      this.gv2     = gv2;
      this.storage = storage;
    }

    public boolean isUuid() {
      return uuid;
    }

    public boolean isGv2() {
      return gv2;
    }

    public boolean isStorage() {
      return storage;
    }
  }
}
