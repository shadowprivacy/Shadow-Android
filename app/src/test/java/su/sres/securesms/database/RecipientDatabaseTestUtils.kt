package su.sres.securesms.database

import android.net.Uri
import org.signal.zkgroup.profiles.ProfileKeyCredential
import su.sres.securesms.conversation.colors.AvatarColor
import su.sres.securesms.conversation.colors.ChatColors
import su.sres.securesms.groups.GroupId
import su.sres.securesms.profiles.ProfileName
import su.sres.securesms.recipients.Recipient
import su.sres.securesms.recipients.RecipientDetails
import su.sres.securesms.recipients.RecipientId
import su.sres.securesms.wallpaper.ChatWallpaper
import org.whispersystems.libsignal.util.guava.Optional
import java.util.UUID
import kotlin.random.Random

/**
 * Test utilities to create recipients in different states.
 */
object RecipientDatabaseTestUtils {

  fun createRecipient(
    resolved: Boolean = false,
    groupName: String? = null,
    groupAvatarId: Optional<Long> = Optional.absent(),
    systemContact: Boolean = false,
    isSelf: Boolean = false,
    participants: List<Recipient> = listOf(),
    recipientId: RecipientId = RecipientId.from(Random.nextLong()),
    uuid: UUID? = UUID.randomUUID(),
    username: String? = null,
    e164: String? = null,
    email: String? = null,
    groupId: GroupId? = null,
    groupType: RecipientDatabase.GroupType = RecipientDatabase.GroupType.NONE,
    blocked: Boolean = false,
    muteUntil: Long = -1,
    messageVibrateState: RecipientDatabase.VibrateState = RecipientDatabase.VibrateState.DEFAULT,
    callVibrateState: RecipientDatabase.VibrateState = RecipientDatabase.VibrateState.DEFAULT,
    messageRingtone: Uri = Uri.EMPTY,
    callRingtone: Uri = Uri.EMPTY,
    defaultSubscriptionId: Int = 0,
    expireMessages: Int = 0,
    registered: RecipientDatabase.RegisteredState = RecipientDatabase.RegisteredState.REGISTERED,
    profileKey: ByteArray = Random.nextBytes(32),
    profileKeyCredential: ProfileKeyCredential? = null,
    systemProfileName: ProfileName = ProfileName.EMPTY,
    systemDisplayName: String? = null,
    systemContactPhoto: String? = null,
    systemPhoneLabel: String? = null,
    systemContactUri: String? = null,
    signalProfileName: ProfileName = ProfileName.EMPTY,
    signalProfileAvatar: String? = null,
    hasProfileImage: Boolean = false,
    profileSharing: Boolean = false,
    lastProfileFetch: Long = 0L,
    notificationChannel: String? = null,
    unidentifiedAccessMode: RecipientDatabase.UnidentifiedAccessMode = RecipientDatabase.UnidentifiedAccessMode.UNKNOWN,
    forceSmsSelection: Boolean = false,
    capabilities: Long = 0L,
    insightBannerTier: RecipientDatabase.InsightsBannerTier = RecipientDatabase.InsightsBannerTier.NO_TIER,
    storageId: ByteArray? = null,
    mentionSetting: RecipientDatabase.MentionSetting = RecipientDatabase.MentionSetting.ALWAYS_NOTIFY,
    wallpaper: ChatWallpaper? = null,
    chatColors: ChatColors? = null,
    avatarColor: AvatarColor = AvatarColor.BLUE,
    about: String? = null,
    aboutEmoji: String? = null,
    syncExtras: RecipientDatabase.RecipientSettings.SyncExtras = RecipientDatabase.RecipientSettings.SyncExtras(
      null,
      null,
      null,
      IdentityDatabase.VerifiedStatus.DEFAULT,
      false,
      false
    ),
    extras: Recipient.Extras? = null,
    hasGroupsInCommon: Boolean = false
  ): Recipient = Recipient(
    recipientId,
    RecipientDetails(
      groupName,
      systemDisplayName,
      groupAvatarId,
      systemContact,
      isSelf,
      registered,
      RecipientDatabase.RecipientSettings(
        recipientId,
        uuid,
        username,
        e164,
        email,
        groupId,
        groupType,
        blocked,
        muteUntil,
        messageVibrateState,
        callVibrateState,
        messageRingtone,
        callRingtone,
        defaultSubscriptionId,
        expireMessages,
        registered,
        profileKey,
        profileKeyCredential,
        systemProfileName,
        systemDisplayName,
        systemContactPhoto,
        systemPhoneLabel,
        systemContactUri,
        signalProfileName,
        signalProfileAvatar,
        hasProfileImage,
        profileSharing,
        lastProfileFetch,
        notificationChannel,
        unidentifiedAccessMode,
        forceSmsSelection,
        capabilities,
        insightBannerTier,
        storageId,
        mentionSetting,
        wallpaper,
        chatColors,
        avatarColor,
        about,
        aboutEmoji,
        syncExtras,
        extras,
        hasGroupsInCommon
      ),
      participants
    ),
    resolved
  )
}