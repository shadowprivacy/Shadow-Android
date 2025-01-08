package su.sres.securesms.avatar.picker

import android.content.Context
import android.net.Uri
import android.widget.Toast
import io.reactivex.rxjava3.core.Single
import su.sres.core.util.StreamUtil
import su.sres.core.util.ThreadUtil
import su.sres.core.util.concurrent.SignalExecutors
import su.sres.core.util.logging.Log
import su.sres.securesms.R
import su.sres.securesms.avatar.Avatar
import su.sres.securesms.avatar.AvatarPickerStorage
import su.sres.securesms.avatar.AvatarRenderer
import su.sres.securesms.avatar.Avatars
import su.sres.securesms.conversation.colors.AvatarColor
import su.sres.securesms.database.ShadowDatabase
import su.sres.securesms.groups.GroupId
import su.sres.securesms.mediasend.Media
import su.sres.securesms.profiles.AvatarHelper
import su.sres.securesms.providers.BlobProvider
import su.sres.securesms.recipients.Recipient
import su.sres.securesms.util.NameUtil
import su.sres.signalservice.api.util.StreamDetails
import java.io.IOException

private val TAG = Log.tag(AvatarPickerRepository::class.java)

class AvatarPickerRepository(context: Context) {

  private val applicationContext = context.applicationContext

  fun getAvatarForSelf(): Single<Avatar> = Single.fromCallable {
    val details: StreamDetails? = AvatarHelper.getSelfProfileAvatarStream(applicationContext)
    if (details != null) {
      try {
        val bytes = StreamUtil.readFully(details.stream)
        Avatar.Photo(
          BlobProvider.getInstance().forData(bytes).createForSingleSessionInMemory(),
          details.length,
          Avatar.DatabaseId.DoNotPersist
        )
      } catch (e: IOException) {
        Log.w(TAG, "Failed to read avatar!")
        getDefaultAvatarForSelf()
      }
    } else {
      getDefaultAvatarForSelf()
    }
  }

  fun getAvatarForGroup(groupId: GroupId): Single<Avatar> = Single.fromCallable {
    val recipient = Recipient.externalGroupExact(applicationContext, groupId)

    if (AvatarHelper.hasAvatar(applicationContext, recipient.id)) {
      try {
        val bytes = AvatarHelper.getAvatarBytes(applicationContext, recipient.id)
        Avatar.Photo(
          BlobProvider.getInstance().forData(bytes).createForSingleSessionInMemory(),
          AvatarHelper.getAvatarLength(applicationContext, recipient.id),
          Avatar.DatabaseId.DoNotPersist
        )
      } catch (e: IOException) {
        Log.w(TAG, "Failed to read group avatar!")
        getDefaultAvatarForGroup(recipient.avatarColor)
      }
    } else {
      getDefaultAvatarForGroup(recipient.avatarColor)
    }
  }

  fun getPersistedAvatarsForSelf(): Single<List<Avatar>> = Single.fromCallable {
    ShadowDatabase.avatarPicker.getAvatarsForSelf()
  }

  fun getPersistedAvatarsForGroup(groupId: GroupId): Single<List<Avatar>> = Single.fromCallable {
    ShadowDatabase.avatarPicker.getAvatarsForGroup(groupId)
  }

  fun getDefaultAvatarsForSelf(): Single<List<Avatar>> = Single.fromCallable {
    Avatars.defaultAvatarsForSelf.entries.mapIndexed { index, entry ->
      Avatar.Vector(entry.key, color = Avatars.colors[index % Avatars.colors.size], Avatar.DatabaseId.NotSet)
    }
  }

  fun getDefaultAvatarsForGroup(): Single<List<Avatar>> = Single.fromCallable {
    Avatars.defaultAvatarsForGroup.entries.mapIndexed { index, entry ->
      Avatar.Vector(entry.key, color = Avatars.colors[index % Avatars.colors.size], Avatar.DatabaseId.NotSet)
    }
  }

  fun writeMediaToMultiSessionStorage(media: Media, onMediaWrittenToMultiSessionStorage: (Uri) -> Unit) {
    SignalExecutors.BOUNDED.execute {
      onMediaWrittenToMultiSessionStorage(AvatarPickerStorage.save(applicationContext, media))
    }
  }

  fun persistAvatarForSelf(avatar: Avatar, onPersisted: (Avatar) -> Unit) {
    SignalExecutors.BOUNDED.execute {
      val avatarDatabase = ShadowDatabase.avatarPicker
      val savedAvatar = avatarDatabase.saveAvatarForSelf(avatar)
      avatarDatabase.markUsage(savedAvatar)
      onPersisted(savedAvatar)
    }
  }

  fun persistAvatarForGroup(avatar: Avatar, groupId: GroupId, onPersisted: (Avatar) -> Unit) {
    SignalExecutors.BOUNDED.execute {
      val avatarDatabase = ShadowDatabase.avatarPicker
      val savedAvatar = avatarDatabase.saveAvatarForGroup(avatar, groupId)
      avatarDatabase.markUsage(savedAvatar)
      onPersisted(savedAvatar)
    }
  }

  fun persistAndCreateMediaForSelf(avatar: Avatar, onSaved: (Media) -> Unit) {
    SignalExecutors.BOUNDED.execute {
      if (avatar.databaseId !is Avatar.DatabaseId.DoNotPersist) {
        persistAvatarForSelf(avatar) {
          AvatarRenderer.renderAvatar(applicationContext, avatar, onSaved, this::handleRenderFailure)
        }
      } else {
        AvatarRenderer.renderAvatar(applicationContext, avatar, onSaved, this::handleRenderFailure)
      }
    }
  }

  fun persistAndCreateMediaForGroup(avatar: Avatar, groupId: GroupId, onSaved: (Media) -> Unit) {
    SignalExecutors.BOUNDED.execute {
      if (avatar.databaseId !is Avatar.DatabaseId.DoNotPersist) {
        persistAvatarForGroup(avatar, groupId) {
          AvatarRenderer.renderAvatar(applicationContext, avatar, onSaved, this::handleRenderFailure)
        }
      } else {
        AvatarRenderer.renderAvatar(applicationContext, avatar, onSaved, this::handleRenderFailure)
      }
    }
  }

  fun createMediaForNewGroup(avatar: Avatar, onSaved: (Media) -> Unit) {
    SignalExecutors.BOUNDED.execute {
      AvatarRenderer.renderAvatar(applicationContext, avatar, onSaved, this::handleRenderFailure)
    }
  }

  fun handleRenderFailure(throwable: Throwable?) {
    Log.w(TAG, "Failed to render avatar.", throwable)
    ThreadUtil.postToMain {
      Toast.makeText(applicationContext, R.string.AvatarPickerRepository__failed_to_save_avatar, Toast.LENGTH_SHORT).show()
    }
  }

  fun getDefaultAvatarForSelf(): Avatar {
    val initials = NameUtil.getAbbreviation(Recipient.self().getDisplayName(applicationContext))

    return if (initials.isNullOrBlank()) {
      Avatar.getDefaultForSelf()
    } else {
      Avatar.Text(initials, requireNotNull(Avatars.colorMap[Recipient.self().avatarColor.serialize()]), Avatar.DatabaseId.DoNotPersist)
    }
  }

  fun getDefaultAvatarForGroup(groupId: GroupId): Avatar {
    val recipient = Recipient.externalGroupExact(applicationContext, groupId)

    return getDefaultAvatarForGroup(recipient.avatarColor)
  }

  fun getDefaultAvatarForGroup(color: AvatarColor?): Avatar {
    val colorPair = Avatars.colorMap[color?.serialize()]
    val defaultColor = Avatar.getDefaultForGroup()

    return if (colorPair != null) {
      defaultColor.copy(color = colorPair)
    } else {
      defaultColor
    }
  }

  fun delete(avatar: Avatar, onDelete: () -> Unit) {
    SignalExecutors.BOUNDED.execute {
      if (avatar.databaseId is Avatar.DatabaseId.Saved) {
        val avatarDatabase = ShadowDatabase.avatarPicker
        avatarDatabase.deleteAvatar(avatar)
      }
      onDelete()
    }
  }
}