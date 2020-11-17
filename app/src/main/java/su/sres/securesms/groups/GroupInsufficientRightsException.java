package su.sres.securesms.groups;

public final class GroupInsufficientRightsException extends GroupChangeException {

    GroupInsufficientRightsException(Throwable throwable) {
        super(throwable);
    }
}