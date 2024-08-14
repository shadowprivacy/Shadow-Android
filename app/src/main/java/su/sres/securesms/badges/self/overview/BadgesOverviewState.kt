package su.sres.securesms.badges.self.overview

import su.sres.securesms.badges.models.Badge

data class BadgesOverviewState(
  val stage: Stage = Stage.INIT,
  val allUnlockedBadges: List<Badge> = listOf(),
  val featuredBadge: Badge? = null,
  val displayBadgesOnProfile: Boolean = false
) {
  enum class Stage {
    INIT,
    READY,
    UPDATING
  }
}