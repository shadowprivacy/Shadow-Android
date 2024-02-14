package su.sres.securesms.database.model

import su.sres.securesms.recipients.RecipientId
import su.sres.signalservice.api.crypto.ContentHint
import su.sres.signalservice.internal.push.SignalServiceProtos

/**
 * Model class for reading from the [su.sres.securesms.database.MessageSendLogDatabase].
 */
data class MessageLogEntry(
  val recipientId: RecipientId,
  val dateSent: Long,
  val content: SignalServiceProtos.Content,
  val contentHint: ContentHint,
  val relatedMessageId: Long,
  val isRelatedMessageMms: Boolean,
) {
  val hasRelatedMessage: Boolean
    @JvmName("hasRelatedMessage")
    get() = relatedMessageId > 0
}