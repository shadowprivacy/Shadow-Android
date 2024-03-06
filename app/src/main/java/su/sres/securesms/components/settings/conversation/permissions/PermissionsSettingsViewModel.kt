package su.sres.securesms.components.settings.conversation.permissions

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import su.sres.securesms.groups.GroupAccessControl
import su.sres.securesms.groups.GroupId
import su.sres.securesms.groups.LiveGroup
import su.sres.securesms.recipients.Recipient
import su.sres.securesms.util.FeatureFlags
import su.sres.securesms.util.SingleLiveEvent
import su.sres.securesms.util.livedata.Store

class PermissionsSettingsViewModel(
  private val groupId: GroupId,
  private val repository: PermissionsSettingsRepository
) : ViewModel() {

  private val store = Store(PermissionsSettingsState())
  private val liveGroup = LiveGroup(groupId)
  private val internalEvents = SingleLiveEvent<PermissionsSettingsEvents>()

  val state: LiveData<PermissionsSettingsState> = store.stateLiveData
  val events: LiveData<PermissionsSettingsEvents> = internalEvents

  init {
    store.update(liveGroup.isSelfAdmin) { isSelfAdmin, state ->
      state.copy(selfCanEditSettings = isSelfAdmin)
    }

    store.update(liveGroup.membershipAdditionAccessControl) { membershipAdditionAccessControl, state ->
      state.copy(nonAdminCanAddMembers = membershipAdditionAccessControl == GroupAccessControl.ALL_MEMBERS)
    }

    store.update(liveGroup.attributesAccessControl) { attributesAccessControl, state ->
      state.copy(nonAdminCanEditGroupInfo = attributesAccessControl == GroupAccessControl.ALL_MEMBERS)
    }

    store.update(liveGroup.isAnnouncementGroup) { isAnnouncementGroup, state ->
      state.copy(
        announcementGroup = isAnnouncementGroup,
        announcementGroupPermissionEnabled = state.announcementGroupPermissionEnabled || isAnnouncementGroup
      )
    }

    store.update(liveGroup.groupRecipient) { groupRecipient, state ->
      val allHaveCapability = groupRecipient.participants.map { it.announcementGroupCapability }.all { it == Recipient.Capability.SUPPORTED }
      state.copy(announcementGroupPermissionEnabled = (FeatureFlags.announcementGroups() && allHaveCapability) || state.announcementGroup)
    }
  }

  fun setNonAdminCanAddMembers(nonAdminCanAddMembers: Boolean) {
    repository.applyMembershipRightsChange(groupId, nonAdminCanAddMembers.asGroupAccessControl()) { reason ->
      internalEvents.postValue(PermissionsSettingsEvents.GroupChangeError(reason))
    }
  }

  fun setNonAdminCanEditGroupInfo(nonAdminCanEditGroupInfo: Boolean) {
    repository.applyAttributesRightsChange(groupId, nonAdminCanEditGroupInfo.asGroupAccessControl()) { reason ->
      internalEvents.postValue(PermissionsSettingsEvents.GroupChangeError(reason))
    }
  }

  fun setAnnouncementGroup(announcementGroup: Boolean) {
    repository.applyAnnouncementGroupChange(groupId, announcementGroup) { reason ->
      internalEvents.postValue(PermissionsSettingsEvents.GroupChangeError(reason))
    }
  }

  private fun Boolean.asGroupAccessControl(): GroupAccessControl {
    return if (this) {
      GroupAccessControl.ALL_MEMBERS
    } else {
      GroupAccessControl.ONLY_ADMINS
    }
  }

  class Factory(
    private val groupId: GroupId,
    private val repository: PermissionsSettingsRepository
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
      return requireNotNull(modelClass.cast(PermissionsSettingsViewModel(groupId, repository)))
    }
  }
}