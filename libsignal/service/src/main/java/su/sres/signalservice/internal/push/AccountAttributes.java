/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package su.sres.signalservice.internal.push;

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
  private SignalServiceProfile.Capabilities capabilities;

  public AccountAttributes(String signalingKey,
                           int registrationId,
                           boolean fetchesMessages,
                           String pin,
                           byte[] unidentifiedAccessKey,
                           boolean unrestrictedUnidentifiedAccess,
                           SignalServiceProfile.Capabilities capabilities,
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

  public SignalServiceProfile.Capabilities getCapabilities() {
    return capabilities;
  }
}
