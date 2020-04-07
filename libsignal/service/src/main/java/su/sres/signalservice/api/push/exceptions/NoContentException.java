/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package su.sres.signalservice.api.push.exceptions;

public class NoContentException extends NonSuccessfulResponseCodeException {
    public NoContentException(String s) {
        super(s);
    }
}