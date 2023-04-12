package su.sres.signalservice.internal.push.exceptions;

import su.sres.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;

public final class GroupPatchNotAcceptedException extends NonSuccessfulResponseCodeException {
    public GroupPatchNotAcceptedException() {
        super(400);
    }
}