package su.sres.securesms.groups;

public final class GroupNotAMemberException extends GroupChangeException {

    public GroupNotAMemberException(Throwable throwable) {
        super(throwable);
    }

    GroupNotAMemberException() {
    }
}