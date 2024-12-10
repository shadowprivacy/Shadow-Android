/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package su.sres.signalservice.api.util;

import java.util.UUID;

import su.sres.signalservice.api.push.ACI;

public interface CredentialsProvider {
  ACI getAci();
  String getUserLogin();
  String getPassword();
}
