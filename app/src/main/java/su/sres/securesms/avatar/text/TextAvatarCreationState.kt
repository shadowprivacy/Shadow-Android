package su.sres.securesms.avatar.text

import su.sres.securesms.avatar.Avatar
import su.sres.securesms.avatar.AvatarColorItem
import su.sres.securesms.avatar.Avatars

data class TextAvatarCreationState(
  val currentAvatar: Avatar.Text,
) {
  fun colors(): List<AvatarColorItem> = Avatars.colors.map { AvatarColorItem(it, currentAvatar.color == it) }
}