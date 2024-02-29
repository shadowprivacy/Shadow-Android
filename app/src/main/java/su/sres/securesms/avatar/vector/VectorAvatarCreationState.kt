package su.sres.securesms.avatar.vector

import su.sres.securesms.avatar.Avatar
import su.sres.securesms.avatar.AvatarColorItem
import su.sres.securesms.avatar.Avatars

data class VectorAvatarCreationState(
  val currentAvatar: Avatar.Vector,
) {
  fun colors(): List<AvatarColorItem> = Avatars.colors.map { AvatarColorItem(it, currentAvatar.color == it) }
}