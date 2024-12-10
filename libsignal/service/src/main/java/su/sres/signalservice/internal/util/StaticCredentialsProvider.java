/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package su.sres.signalservice.internal.util;

import su.sres.signalservice.api.push.ACI;
import su.sres.signalservice.api.util.CredentialsProvider;

import java.util.UUID;

public class StaticCredentialsProvider implements CredentialsProvider {

  private final ACI    aci;
  private final String userLogin;
  private final String password;
  public StaticCredentialsProvider(ACI aci, String userLogin, String password) {
    this.aci      = aci;
    this.userLogin     = userLogin;
    this.password = password;
  }

  @Override
  public ACI getAci() {
    return aci;
  }

  @Override
  public String getUserLogin() {
    return userLogin;
  }

  @Override
  public String getPassword() {
    return password;
  }
}
