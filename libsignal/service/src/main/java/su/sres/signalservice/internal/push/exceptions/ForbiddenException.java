package su.sres.signalservice.internal.push.exceptions;

import su.sres.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;

public final class ForbiddenException extends NonSuccessfulResponseCodeException {
    public ForbiddenException() {
        super(403);
    }
}