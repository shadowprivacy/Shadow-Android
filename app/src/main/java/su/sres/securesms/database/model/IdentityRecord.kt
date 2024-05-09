package su.sres.securesms.database.model

import su.sres.securesms.database.IdentityDatabase
import su.sres.securesms.recipients.RecipientId
import org.whispersystems.libsignal.IdentityKey

data class IdentityRecord(
  val recipientId: RecipientId,
  val identityKey: IdentityKey,
  val verifiedStatus: IdentityDatabase.VerifiedStatus,
  @get:JvmName("isFirstUse")
  val firstUse: Boolean,
  val timestamp: Long,
  @get:JvmName("isApprovedNonBlocking")
  val nonblockingApproval: Boolean
)