package su.sres.securesms.groups;

import androidx.annotation.NonNull;

public final class GroupJoinAlreadyAMemberException extends GroupChangeException {

    GroupJoinAlreadyAMemberException(@NonNull Throwable throwable) {
        super(throwable);
    }
}
