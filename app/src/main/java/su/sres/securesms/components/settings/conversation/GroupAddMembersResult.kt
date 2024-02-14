package su.sres.securesms.components.settings.conversation

import su.sres.securesms.groups.ui.GroupChangeFailureReason
import su.sres.securesms.recipients.Recipient

sealed class GroupAddMembersResult {
  class Success(
    val numberOfMembersAdded: Int,
    val newMembersInvited: List<Recipient>
  ) : GroupAddMembersResult()

  class Failure(
    val reason: GroupChangeFailureReason
  ) : GroupAddMembersResult()
}