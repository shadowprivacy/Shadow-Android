package su.sres.securesms.components.settings.conversation

import su.sres.securesms.groups.GroupId
import su.sres.securesms.groups.ui.GroupChangeFailureReason
import su.sres.securesms.recipients.Recipient
import su.sres.securesms.recipients.RecipientId

sealed class ConversationSettingsEvent {
  class AddToAGroup(
    val recipientId: RecipientId,
    val groupMembership: List<RecipientId>
  ) : ConversationSettingsEvent()

  class AddMembersToGroup(
    val groupId: GroupId,
    val selectionWarning: Int,
    val selectionLimit: Int,
    val isAnnouncementGroup: Boolean,
    val groupMembersWithoutSelf: List<RecipientId>
  ) : ConversationSettingsEvent()

  object ShowGroupHardLimitDialog : ConversationSettingsEvent()

  class ShowAddMembersToGroupError(
    val failureReason: GroupChangeFailureReason
  ) : ConversationSettingsEvent()

  class ShowGroupInvitesSentDialog(
    val invitesSentTo: List<Recipient>
  ) : ConversationSettingsEvent()

  class ShowMembersAdded(
    val membersAddedCount: Int
  ) : ConversationSettingsEvent()

  class InitiateGroupMigration(
    val recipientId: RecipientId
  ) : ConversationSettingsEvent()
}