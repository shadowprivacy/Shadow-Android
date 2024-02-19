package su.sres.securesms.components.settings.conversation

import android.content.Context
import android.database.Cursor
import androidx.annotation.WorkerThread
import androidx.lifecycle.LiveData
import org.whispersystems.libsignal.util.guava.Optional
import org.whispersystems.libsignal.util.guava.Preconditions
import su.sres.core.util.concurrent.SignalExecutors
import su.sres.core.util.logging.Log
import su.sres.storageservice.protos.groups.local.DecryptedGroup
import su.sres.storageservice.protos.groups.local.DecryptedPendingMember
import su.sres.securesms.contacts.sync.DirectoryHelper
import su.sres.securesms.database.DatabaseFactory
import su.sres.securesms.database.GroupDatabase
import su.sres.securesms.database.IdentityDatabase
import su.sres.securesms.database.MediaDatabase
import su.sres.securesms.groups.GroupId
import su.sres.securesms.groups.GroupManager
import su.sres.securesms.groups.GroupProtoUtil
import su.sres.securesms.groups.LiveGroup
import su.sres.securesms.groups.ui.GroupChangeFailureReason
import su.sres.securesms.keyvalue.SignalStore
import su.sres.securesms.recipients.Recipient
import su.sres.securesms.recipients.RecipientId
import su.sres.securesms.recipients.RecipientUtil
import su.sres.securesms.util.FeatureFlags
import java.io.IOException

private val TAG = Log.tag(ConversationSettingsRepository::class.java)

class ConversationSettingsRepository(
  private val context: Context
) {

  @WorkerThread
  fun getThreadMedia(threadId: Long): Optional<Cursor> {
    return if (threadId <= 0) {
      Optional.absent()
    } else {
      Optional.of(DatabaseFactory.getMediaDatabase(context).getGalleryMediaForThread(threadId, MediaDatabase.Sorting.Newest))
    }
  }

  fun getThreadId(recipientId: RecipientId, consumer: (Long) -> Unit) {
    SignalExecutors.BOUNDED.execute {
      consumer(DatabaseFactory.getThreadDatabase(context).getThreadIdIfExistsFor(recipientId))
    }
  }

  fun getThreadId(groupId: GroupId, consumer: (Long) -> Unit) {
    SignalExecutors.BOUNDED.execute {
      val recipientId = Recipient.externalGroupExact(context, groupId).id
      consumer(DatabaseFactory.getThreadDatabase(context).getThreadIdIfExistsFor(recipientId))
    }
  }

  fun isInternalRecipientDetailsEnabled(): Boolean = SignalStore.internalValues().recipientDetails()

  fun hasGroups(consumer: (Boolean) -> Unit) {
    SignalExecutors.BOUNDED.execute { consumer(DatabaseFactory.getGroupDatabase(context).activeGroupCount > 0) }
  }

  fun getIdentity(recipientId: RecipientId, consumer: (IdentityDatabase.IdentityRecord?) -> Unit) {
    SignalExecutors.BOUNDED.execute {
      consumer(
        DatabaseFactory.getIdentityDatabase(context)
          .getIdentity(recipientId)
          .orNull()
      )
    }
  }

  fun getGroupsInCommon(recipientId: RecipientId, consumer: (List<Recipient>) -> Unit) {
    SignalExecutors.BOUNDED.execute {
      consumer(
        DatabaseFactory
          .getGroupDatabase(context)
          .getPushGroupsContainingMember(recipientId)
          .asSequence()
          .filter { it.members.contains(Recipient.self().id) }
          .map(GroupDatabase.GroupRecord::getRecipientId)
          .map(Recipient::resolved)
          .sortedBy { gr -> gr.getDisplayName(context) }
          .toList()
      )
    }
  }

  fun getGroupMembership(recipientId: RecipientId, consumer: (List<RecipientId>) -> Unit) {
    SignalExecutors.BOUNDED.execute {
      val groupDatabase = DatabaseFactory.getGroupDatabase(context)
      val groupRecords = groupDatabase.getPushGroupsContainingMember(recipientId)
      val groupRecipients = ArrayList<RecipientId>(groupRecords.size)
      for (groupRecord in groupRecords) {
        groupRecipients.add(groupRecord.recipientId)
      }
      consumer(groupRecipients)
    }
  }

  /* fun refreshRecipient(recipientId: RecipientId) {
    SignalExecutors.UNBOUNDED.execute {
      try {
        DirectoryHelper.refreshDirectoryFor(context, Recipient.resolved(recipientId), false)
      } catch (e: IOException) {
        Log.w(TAG, "Failed to refresh user after adding to contacts.")
      }
    }
  } */

  fun setMuteUntil(recipientId: RecipientId, until: Long) {
    SignalExecutors.BOUNDED.execute {
      DatabaseFactory.getRecipientDatabase(context).setMuted(recipientId, until)
    }
  }

  fun getGroupCapacity(groupId: GroupId, consumer: (GroupCapacityResult) -> Unit) {
    SignalExecutors.BOUNDED.execute {
      val groupRecord: GroupDatabase.GroupRecord = DatabaseFactory.getGroupDatabase(context).getGroup(groupId).get()
      consumer(
        if (groupRecord.isV2Group) {
          val decryptedGroup: DecryptedGroup = groupRecord.requireV2GroupProperties().decryptedGroup
          val pendingMembers: List<RecipientId> = decryptedGroup.pendingMembersList
            .map(DecryptedPendingMember::getUuid)
            .map(GroupProtoUtil::uuidByteStringToRecipientId)

          val members = mutableListOf<RecipientId>()

          members.addAll(groupRecord.members)
          members.addAll(pendingMembers)

          GroupCapacityResult(Recipient.self().id, members, FeatureFlags.groupLimits())
        } else {
          GroupCapacityResult(Recipient.self().id, groupRecord.members, FeatureFlags.groupLimits())
        }
      )
    }
  }

  fun addMembers(groupId: GroupId, selected: List<RecipientId>, consumer: (GroupAddMembersResult) -> Unit) {
    SignalExecutors.BOUNDED.execute {
      consumer(
        try {
          val groupActionResult = GroupManager.addMembers(context, groupId.requirePush(), selected)
          GroupAddMembersResult.Success(groupActionResult.addedMemberCount, Recipient.resolvedList(groupActionResult.invitedMembers))
        } catch (e: Exception) {
          GroupAddMembersResult.Failure(GroupChangeFailureReason.fromException(e))
        }
      )
    }
  }

  fun setMuteUntil(groupId: GroupId, until: Long) {
    SignalExecutors.BOUNDED.execute {
      val recipientId = Recipient.externalGroupExact(context, groupId).id
      DatabaseFactory.getRecipientDatabase(context).setMuted(recipientId, until)
    }
  }

  fun block(recipientId: RecipientId) {
    SignalExecutors.BOUNDED.execute {
      val recipient = Recipient.resolved(recipientId)
      RecipientUtil.blockNonGroup(context, recipient)
    }
  }

  fun unblock(recipientId: RecipientId) {
    SignalExecutors.BOUNDED.execute {
      val recipient = Recipient.resolved(recipientId)
      RecipientUtil.unblock(context, recipient)
    }
  }

  fun block(groupId: GroupId) {
    SignalExecutors.BOUNDED.execute {
      val recipient = Recipient.externalGroupExact(context, groupId)
      RecipientUtil.block(context, recipient)
    }
  }

  fun unblock(groupId: GroupId) {
    SignalExecutors.BOUNDED.execute {
      val recipient = Recipient.externalGroupExact(context, groupId)
      RecipientUtil.unblock(context, recipient)
    }
  }

  fun disableProfileSharingForInternalUser(recipientId: RecipientId) {
    Preconditions.checkArgument(FeatureFlags.internalUser(), "Internal users only!")
    SignalExecutors.BOUNDED.execute {
      DatabaseFactory.getRecipientDatabase(context).setProfileSharing(recipientId, false)
    }
  }

  fun deleteSessionForInternalUser(recipientId: RecipientId) {
    Preconditions.checkArgument(FeatureFlags.internalUser(), "Internal users only!")

    SignalExecutors.BOUNDED.execute {
      DatabaseFactory.getSessionDatabase(context).deleteAllFor(recipientId)
    }
  }

  @WorkerThread
  fun isMessageRequestAccepted(recipient: Recipient): Boolean {
    return RecipientUtil.isMessageRequestAccepted(context, recipient)
  }

  fun getMembershipCountDescription(liveGroup: LiveGroup): LiveData<String> {
    return liveGroup.getMembershipCountDescription(context.resources)
  }

  fun getExternalPossiblyMigratedGroupRecipientId(groupId: GroupId, consumer: (RecipientId) -> Unit) {
    SignalExecutors.BOUNDED.execute {
      consumer(Recipient.externalPossiblyMigratedGroup(context, groupId).id)
    }
  }
}