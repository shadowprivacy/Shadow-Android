package su.sres.signalservice.internal.push.exceptions;

import su.sres.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;

public final class GroupExistsException extends NonSuccessfulResponseCodeException {
    public GroupExistsException() {
        super(409);
    }
}
