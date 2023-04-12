/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package su.sres.signalservice.api.push.exceptions;

public class AuthorizationFailedException extends NonSuccessfulResponseCodeException {
  public AuthorizationFailedException(int code, String s) {
    super(code, s);
  }
}
