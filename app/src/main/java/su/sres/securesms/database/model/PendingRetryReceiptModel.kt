package su.sres.securesms.database.model

import su.sres.securesms.recipients.RecipientId

/** A model for [su.sres.securesms.database.PendingRetryReceiptDatabase] */
data class PendingRetryReceiptModel(
  val id: Long,
  val author: RecipientId,
  val authorDevice: Int,
  val sentTimestamp: Long,
  val receivedTimestamp: Long,
  val threadId: Long
)