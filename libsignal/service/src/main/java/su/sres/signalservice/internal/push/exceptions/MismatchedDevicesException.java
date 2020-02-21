/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package su.sres.signalservice.internal.push.exceptions;

import su.sres.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import su.sres.signalservice.internal.push.MismatchedDevices;

public class MismatchedDevicesException extends NonSuccessfulResponseCodeException {

  private final MismatchedDevices mismatchedDevices;

  public MismatchedDevicesException(MismatchedDevices mismatchedDevices) {
    this.mismatchedDevices = mismatchedDevices;
  }

  public MismatchedDevices getMismatchedDevices() {
    return mismatchedDevices;
  }
}
