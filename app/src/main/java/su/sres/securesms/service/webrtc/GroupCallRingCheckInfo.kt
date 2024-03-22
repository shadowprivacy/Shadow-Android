package su.sres.securesms.service.webrtc

import org.signal.ringrtc.CallManager
import su.sres.securesms.groups.GroupId
import su.sres.securesms.recipients.RecipientId
import java.util.UUID

data class GroupCallRingCheckInfo(
  val recipientId: RecipientId,
  val groupId: GroupId.V2,
  val ringId: Long,
  val ringerUuid: UUID,
  val ringUpdate: CallManager.RingUpdate
)