package su.sres.securesms.sms;

import androidx.annotation.NonNull;

import su.sres.storageservice.protos.groups.local.DecryptedGroupChange;
import su.sres.securesms.mms.MessageGroupContext;
import su.sres.signalservice.api.groupsv2.DecryptedGroupUtil;

/**
 * Helper util for inspecting GV2 {@link MessageGroupContext} for various message processing.
 */
public final class GroupV2UpdateMessageUtil {

  public static boolean isGroupV2(@NonNull MessageGroupContext groupContext) {
    return groupContext.isV2Group();
  }

  public static boolean isUpdate(@NonNull MessageGroupContext groupContext) {
    return groupContext.isV2Group();
  }

  public static boolean isJustAGroupLeave(@NonNull MessageGroupContext groupContext) {
    if (isGroupV2(groupContext) && isUpdate(groupContext)) {
      DecryptedGroupChange decryptedGroupChange = groupContext.requireGroupV2Properties()
                                                              .getChange();

      return changeEditorOnlyWasRemoved(decryptedGroupChange) &&
             noChangesOtherThanDeletes(decryptedGroupChange);
    }

    return false;
  }

  private static boolean changeEditorOnlyWasRemoved(@NonNull DecryptedGroupChange decryptedGroupChange) {
    return decryptedGroupChange.getDeleteMembersCount() == 1 &&
           decryptedGroupChange.getDeleteMembers(0).equals(decryptedGroupChange.getEditor());
  }

  private static boolean noChangesOtherThanDeletes(@NonNull DecryptedGroupChange decryptedGroupChange) {
    DecryptedGroupChange withoutDeletedMembers = decryptedGroupChange.toBuilder()
                                                                     .clearDeleteMembers()
                                                                     .build();
    return DecryptedGroupUtil.changeIsEmpty(withoutDeletedMembers);
  }
}
