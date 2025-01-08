package su.sres.securesms.badges

import android.content.Context
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers
import su.sres.securesms.badges.models.Badge
import su.sres.securesms.database.RecipientDatabase
import su.sres.securesms.database.ShadowDatabase
import su.sres.securesms.keyvalue.SignalStore
import su.sres.securesms.recipients.Recipient
import su.sres.securesms.util.ProfileUtil

class BadgeRepository(context: Context) {

  private val context = context.applicationContext

  fun setVisibilityForAllBadges(
    displayBadgesOnProfile: Boolean,
    selfBadges: List<Badge> = Recipient.self().badges
  ): Completable = Completable.fromAction {
    val recipientDatabase: RecipientDatabase = ShadowDatabase.recipients
    val badges = selfBadges.map { it.copy(visible = displayBadgesOnProfile) }

    ProfileUtil.uploadProfileWithBadges(context, badges)

    SignalStore.donationsValues().setDisplayBadgesOnProfile(displayBadgesOnProfile)

    recipientDatabase.markNeedsSync(Recipient.self().id)
    // StorageSyncHelper.scheduleSyncForDataChange()

    recipientDatabase.setBadges(Recipient.self().id, badges)
  }.subscribeOn(Schedulers.io())

  fun setFeaturedBadge(featuredBadge: Badge): Completable = Completable.fromAction {
    val badges = Recipient.self().badges
    val reOrderedBadges = listOf(featuredBadge.copy(visible = true)) + (badges.filterNot { it.id == featuredBadge.id })
    ProfileUtil.uploadProfileWithBadges(context, reOrderedBadges)

    val recipientDatabase: RecipientDatabase = ShadowDatabase.recipients
    recipientDatabase.setBadges(Recipient.self().id, reOrderedBadges)
  }.subscribeOn(Schedulers.io())
}