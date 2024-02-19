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
  val relatedMessages: List<MessageId>
) {
  val hasRelatedMessage: Boolean
    @JvmName("hasRelatedMessage")
    get() = relatedMessages.isNotEmpty()
}