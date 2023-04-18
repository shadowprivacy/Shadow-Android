/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package su.sres.signalservice.internal.util;

import su.sres.signalservice.api.util.CredentialsProvider;

import java.util.UUID;

public class StaticCredentialsProvider implements CredentialsProvider {

  private final UUID   uuid;
  private final String userLogin;
  private final String password;
  public StaticCredentialsProvider(UUID uuid, String userLogin, String password) {
    this.uuid         = uuid;
    this.userLogin         = userLogin;
    this.password     = password;
  }

  @Override
  public UUID getUuid() {
    return uuid;
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
