package su.sres.signalservice.internal.push.exceptions;

import su.sres.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;

public final class GroupNotFoundException extends NonSuccessfulResponseCodeException {
    public GroupNotFoundException() {
        super(404);
    }
}
