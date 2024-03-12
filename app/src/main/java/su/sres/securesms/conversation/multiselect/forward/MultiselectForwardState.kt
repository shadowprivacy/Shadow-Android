package su.sres.securesms.conversation.multiselect.forward

import su.sres.securesms.database.IdentityDatabase
import su.sres.securesms.sharing.ShareContact

data class MultiselectForwardState(
  val selectedContacts: List<ShareContact> = emptyList(),
  val stage: Stage = Stage.Selection
) {
  sealed class Stage {
    object Selection : Stage()
    object FirstConfirmation : Stage()
    object LoadingIdentities : Stage()
    data class SafetyConfirmation(val identities: List<IdentityDatabase.IdentityRecord>) : Stage()
    object SendPending : Stage()
    object SomeFailed : Stage()
    object AllFailed : Stage()
    object Success : Stage()
  }
}