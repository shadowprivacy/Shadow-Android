package su.sres.securesms.groups.ui;

import androidx.annotation.NonNull;

import java.io.IOException;

import su.sres.securesms.groups.GroupChangeBusyException;
import su.sres.securesms.groups.GroupInsufficientRightsException;
import su.sres.securesms.groups.GroupNotAMemberException;
import su.sres.securesms.groups.MembershipNotSuitableForV2Exception;

public enum GroupChangeFailureReason {
  NO_RIGHTS,
  NOT_GV2_CAPABLE,
  NOT_ANNOUNCEMENT_CAPABLE,
  NOT_A_MEMBER,
  BUSY,
  NETWORK,
  OTHER;

  public static @NonNull GroupChangeFailureReason fromException(@NonNull Throwable e) {
    if (e instanceof MembershipNotSuitableForV2Exception) return GroupChangeFailureReason.NOT_GV2_CAPABLE;
    if (e instanceof IOException) return GroupChangeFailureReason.NETWORK;
    if (e instanceof GroupNotAMemberException) return GroupChangeFailureReason.NOT_A_MEMBER;
    if (e instanceof GroupChangeBusyException) return GroupChangeFailureReason.BUSY;
    if (e instanceof GroupInsufficientRightsException) return GroupChangeFailureReason.NO_RIGHTS;
    return GroupChangeFailureReason.OTHER;
  }
}