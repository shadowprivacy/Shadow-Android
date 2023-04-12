package su.sres.signalservice.internal.push.exceptions;

import su.sres.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;

public final class NotInGroupException extends NonSuccessfulResponseCodeException {
    public NotInGroupException() {
        super(403);
    }
}