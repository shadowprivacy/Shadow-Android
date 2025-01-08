package su.sres.securesms.components.settings.conversation

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.viewModels
import androidx.navigation.Navigation
import androidx.recyclerview.widget.RecyclerView
import app.cash.exhaustive.Exhaustive
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import su.sres.securesms.AvatarPreviewActivity
import su.sres.securesms.BlockUnblockDialog
import su.sres.securesms.InviteActivity
import su.sres.securesms.MediaPreviewActivity
import su.sres.securesms.MuteDialog
import su.sres.securesms.PushContactSelectionActivity
import su.sres.securesms.R
import su.sres.securesms.VerifyIdentityActivity
import su.sres.securesms.badges.BadgeImageView
import su.sres.securesms.badges.Badges
import su.sres.securesms.badges.Badges.displayBadges
import su.sres.securesms.badges.models.Badge
import su.sres.securesms.badges.view.ViewBadgeBottomSheetDialogFragment
import su.sres.securesms.components.AvatarImageView
import su.sres.securesms.components.recyclerview.OnScrollAnimationHelper
import su.sres.securesms.components.recyclerview.ToolbarShadowAnimationHelper
import su.sres.securesms.components.settings.DSLConfiguration
import su.sres.securesms.components.settings.DSLSettingsAdapter
import su.sres.securesms.components.settings.DSLSettingsFragment
import su.sres.securesms.components.settings.DSLSettingsIcon
import su.sres.securesms.components.settings.DSLSettingsText
import su.sres.securesms.components.settings.NO_TINT
import su.sres.securesms.components.settings.configure
import su.sres.securesms.components.settings.conversation.preferences.AvatarPreference
import su.sres.securesms.components.settings.conversation.preferences.BioTextPreference
import su.sres.securesms.components.settings.conversation.preferences.ButtonStripPreference
import su.sres.securesms.components.settings.conversation.preferences.GroupDescriptionPreference
import su.sres.securesms.components.settings.conversation.preferences.InternalPreference
import su.sres.securesms.components.settings.conversation.preferences.LargeIconClickPreference
import su.sres.securesms.components.settings.conversation.preferences.LegacyGroupPreference
import su.sres.securesms.components.settings.conversation.preferences.RecipientPreference
import su.sres.securesms.components.settings.conversation.preferences.SharedMediaPreference
import su.sres.securesms.components.settings.conversation.preferences.Utils.formatMutedUntil
import su.sres.securesms.contacts.ContactsCursorLoader
import su.sres.securesms.conversation.ConversationIntents
import su.sres.securesms.groups.ParcelableGroupId
import su.sres.securesms.groups.ui.GroupErrors
import su.sres.securesms.groups.ui.GroupLimitDialog
import su.sres.securesms.groups.ui.LeaveGroupDialog
import su.sres.securesms.groups.ui.addmembers.AddMembersActivity
import su.sres.securesms.groups.ui.addtogroup.AddToGroupsActivity
import su.sres.securesms.groups.ui.invitesandrequests.ManagePendingAndRequestingMembersActivity
import su.sres.securesms.groups.ui.managegroup.dialogs.GroupDescriptionDialog
import su.sres.securesms.groups.ui.managegroup.dialogs.GroupInviteSentDialog
import su.sres.securesms.groups.ui.managegroup.dialogs.GroupsLearnMoreBottomSheetDialogFragment
import su.sres.securesms.groups.ui.migration.GroupsV1MigrationInitiationBottomSheetDialogFragment
import su.sres.securesms.mediaoverview.MediaOverviewActivity
import su.sres.securesms.profiles.edit.EditProfileActivity
import su.sres.securesms.recipients.Recipient
import su.sres.securesms.recipients.RecipientExporter
import su.sres.securesms.recipients.RecipientId
import su.sres.securesms.recipients.ui.bottomsheet.RecipientBottomSheetDialogFragment
import su.sres.securesms.recipients.ui.sharablegrouplink.ShareableGroupLinkDialogFragment
import su.sres.securesms.util.CommunicationActions
import su.sres.securesms.util.ContextUtil
import su.sres.securesms.util.ExpirationUtil
import su.sres.securesms.util.FeatureFlags
import su.sres.securesms.util.ThemeUtil
import su.sres.securesms.util.ViewUtil
import su.sres.securesms.util.views.SimpleProgressDialog
import su.sres.securesms.wallpaper.ChatWallpaperActivity

private const val REQUEST_CODE_VIEW_CONTACT = 1
private const val REQUEST_CODE_ADD_CONTACT = 2
private const val REQUEST_CODE_ADD_MEMBERS_TO_GROUP = 3
private const val REQUEST_CODE_RETURN_FROM_MEDIA = 4

class ConversationSettingsFragment : DSLSettingsFragment(
  layoutId = R.layout.conversation_settings_fragment,
  menuId = R.menu.conversation_settings,
  layoutManagerProducer = Badges::createLayoutManagerForGridWithBadges
) {

  private val alertTint by lazy { ContextCompat.getColor(requireContext(), R.color.signal_alert_primary) }
  private val blockIcon by lazy {
    ContextUtil.requireDrawable(requireContext(), R.drawable.ic_block_tinted_24).apply {
      colorFilter = PorterDuffColorFilter(alertTint, PorterDuff.Mode.SRC_IN)
    }
  }

  private val unblockIcon by lazy {
    ContextUtil.requireDrawable(requireContext(), R.drawable.ic_block_tinted_24)
  }

  private val leaveIcon by lazy {
    ContextUtil.requireDrawable(requireContext(), R.drawable.ic_leave_tinted_24).apply {
      colorFilter = PorterDuffColorFilter(alertTint, PorterDuff.Mode.SRC_IN)
    }
  }

  private val viewModel by viewModels<ConversationSettingsViewModel>(
    factoryProducer = {
      val args = ConversationSettingsFragmentArgs.fromBundle(requireArguments())
      val groupId = args.groupId as? ParcelableGroupId

      ConversationSettingsViewModel.Factory(
        recipientId = args.recipientId,
        groupId = ParcelableGroupId.get(groupId),
        repository = ConversationSettingsRepository(requireContext())
      )
    }
  )

  private lateinit var callback: Callback

  private lateinit var toolbar: Toolbar
  private lateinit var toolbarAvatarContainer: FrameLayout
  private lateinit var toolbarAvatar: AvatarImageView
  private lateinit var toolbarBadge: BadgeImageView
  private lateinit var toolbarTitle: TextView
  private lateinit var toolbarBackground: View

  private val navController get() = Navigation.findNavController(requireView())

  override fun onAttach(context: Context) {
    super.onAttach(context)

    callback = context as Callback
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    toolbar = view.findViewById(R.id.toolbar)
    toolbarAvatarContainer = view.findViewById(R.id.toolbar_avatar_container)
    toolbarAvatar = view.findViewById(R.id.toolbar_avatar)
    toolbarBadge = view.findViewById(R.id.toolbar_badge)
    toolbarTitle = view.findViewById(R.id.toolbar_title)
    toolbarBackground = view.findViewById(R.id.toolbar_background)

    super.onViewCreated(view, savedInstanceState)
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    when (requestCode) {
      REQUEST_CODE_ADD_MEMBERS_TO_GROUP -> if (data != null) {
        val selected: List<RecipientId> = requireNotNull(data.getParcelableArrayListExtra(PushContactSelectionActivity.KEY_SELECTED_RECIPIENTS))
        val progress: SimpleProgressDialog.DismissibleDialog = SimpleProgressDialog.showDelayed(requireContext())

        viewModel.onAddToGroupComplete(selected) {
          progress.dismiss()
        }
      }

      REQUEST_CODE_RETURN_FROM_MEDIA -> viewModel.refreshSharedMedia()
      // REQUEST_CODE_ADD_CONTACT -> viewModel.refreshRecipient()
      // REQUEST_CODE_VIEW_CONTACT -> viewModel.refreshRecipient()
    }
  }

  override fun getOnScrollAnimationHelper(toolbarShadow: View): OnScrollAnimationHelper {
    return ConversationSettingsOnUserScrolledAnimationHelper(toolbarAvatarContainer, toolbarTitle, toolbarBackground, toolbarShadow)
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return if (item.itemId == R.id.action_edit) {
      val args = ConversationSettingsFragmentArgs.fromBundle(requireArguments())
      val groupId = args.groupId as ParcelableGroupId

      startActivity(EditProfileActivity.getIntentForGroupProfile(requireActivity(), requireNotNull(ParcelableGroupId.get(groupId))))
      true
    } else {
      super.onOptionsItemSelected(item)
    }
  }

  override fun bindAdapter(adapter: DSLSettingsAdapter) {
    val args = ConversationSettingsFragmentArgs.fromBundle(requireArguments())

    BioTextPreference.register(adapter)
    AvatarPreference.register(adapter)
    ButtonStripPreference.register(adapter)
    LargeIconClickPreference.register(adapter)
    SharedMediaPreference.register(adapter)
    RecipientPreference.register(adapter)
    InternalPreference.register(adapter)
    GroupDescriptionPreference.register(adapter)
    LegacyGroupPreference.register(adapter)

    val recipientId = args.recipientId
    if (recipientId != null) {
      Badge.register(adapter) { badge, _, _ ->
        ViewBadgeBottomSheetDialogFragment.show(parentFragmentManager, recipientId, badge)
      }
    }

    viewModel.state.observe(viewLifecycleOwner) { state ->

      if (state.recipient != Recipient.UNKNOWN) {
        toolbarAvatar.buildOptions()
          .withQuickContactEnabled(false)
          .withUseSelfProfileAvatar(false)
          .withFixedSize(ViewUtil.dpToPx(80))
          .load(state.recipient)

        if (FeatureFlags.displayDonorBadges() && !state.recipient.isSelf) {
          toolbarBadge.setBadgeFromRecipient(state.recipient)
        }

        state.withRecipientSettingsState {
          toolbarTitle.text = state.recipient.getDisplayName(requireContext())
        }

        state.withGroupSettingsState {
          toolbarTitle.text = it.groupTitle
          toolbar.menu.findItem(R.id.action_edit).isVisible = it.canEditGroupAttributes
        }
      }

      adapter.submitList(getConfiguration(state).toMappingModelList()) {
        if (state.isLoaded) {
          (view?.parent as? ViewGroup)?.doOnPreDraw {
            callback.onContentWillRender()
          }
        }
      }
    }

    viewModel.events.observe(viewLifecycleOwner) { event ->
      @Exhaustive
      when (event) {
        is ConversationSettingsEvent.AddToAGroup -> handleAddToAGroup(event)
        is ConversationSettingsEvent.AddMembersToGroup -> handleAddMembersToGroup(event)
        ConversationSettingsEvent.ShowGroupHardLimitDialog -> showGroupHardLimitDialog()
        is ConversationSettingsEvent.ShowAddMembersToGroupError -> showAddMembersToGroupError(event)
        is ConversationSettingsEvent.ShowGroupInvitesSentDialog -> showGroupInvitesSentDialog(event)
        is ConversationSettingsEvent.ShowMembersAdded -> showMembersAdded(event)
        is ConversationSettingsEvent.InitiateGroupMigration -> GroupsV1MigrationInitiationBottomSheetDialogFragment.showForInitiation(parentFragmentManager, event.recipientId)
      }
    }
  }

  private fun getConfiguration(state: ConversationSettingsState): DSLConfiguration {
    return configure {
      if (state.recipient == Recipient.UNKNOWN) {
        return@configure
      }

      customPref(
        AvatarPreference.Model(
          recipient = state.recipient,
          onAvatarClick = { avatar ->
            if (!state.recipient.isSelf) {
              requireActivity().apply {
                startActivity(
                  AvatarPreviewActivity.intentFromRecipientId(this, state.recipient.id),
                  AvatarPreviewActivity.createTransitionBundle(this, avatar)
                )
              }
            }
          },
          onBadgeClick = { badge ->
            ViewBadgeBottomSheetDialogFragment.show(parentFragmentManager, state.recipient.id, badge)
          }
        )
      )

      state.withRecipientSettingsState {
        customPref(BioTextPreference.RecipientModel(recipient = state.recipient))
      }

      state.withGroupSettingsState { groupState ->

        val groupMembershipDescription = if (groupState.groupId.isV1) {
          String.format("%s · %s", groupState.membershipCountDescription, getString(R.string.ManageGroupActivity_legacy_group))
        } else if (!groupState.canEditGroupAttributes && groupState.groupDescription.isNullOrEmpty()) {
          groupState.membershipCountDescription
        } else {
          null
        }

        customPref(
          BioTextPreference.GroupModel(
            groupTitle = groupState.groupTitle,
            groupMembershipDescription = groupMembershipDescription
          )
        )

        if (groupState.groupId.isV2) {
          customPref(
            GroupDescriptionPreference.Model(
              groupId = groupState.groupId,
              groupDescription = groupState.groupDescription,
              descriptionShouldLinkify = groupState.groupDescriptionShouldLinkify,
              canEditGroupAttributes = groupState.canEditGroupAttributes,
              onEditGroupDescription = {
                startActivity(EditProfileActivity.getIntentForGroupProfile(requireActivity(), groupState.groupId))
              },
              onViewGroupDescription = {
                GroupDescriptionDialog.show(childFragmentManager, groupState.groupId, null, groupState.groupDescriptionShouldLinkify)
              }
            )
          )
        } else if (groupState.legacyGroupState != LegacyGroupPreference.State.NONE) {
          customPref(
            LegacyGroupPreference.Model(
              state = groupState.legacyGroupState,
              onLearnMoreClick = { GroupsLearnMoreBottomSheetDialogFragment.show(parentFragmentManager) },
              onUpgradeClick = { viewModel.initiateGroupUpgrade() },
              onMmsWarningClick = { startActivity(Intent(requireContext(), InviteActivity::class.java)) }
            )
          )
        }
      }

      if (state.displayInternalRecipientDetails) {
        customPref(
          InternalPreference.Model(
            recipient = state.recipient,
            onInternalDetailsClicked = {
              val action = ConversationSettingsFragmentDirections.actionConversationSettingsFragmentToInternalDetailsSettingsFragment(state.recipient.id)
              navController.navigate(action)
            }
          )
        )
      }

      customPref(
        ButtonStripPreference.Model(
          state = state.buttonStripState,
          onVideoClick = {
            if (state.recipient.isPushV2Group && state.requireGroupSettingsState().isAnnouncementGroup && !state.requireGroupSettingsState().isSelfAdmin) {
              MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.ConversationActivity_cant_start_group_call)
                .setMessage(R.string.ConversationActivity_only_admins_of_this_group_can_start_a_call)
                .setPositiveButton(android.R.string.ok) { d, _ -> d.dismiss() }
                .show()
            } else {
              CommunicationActions.startVideoCall(requireActivity(), state.recipient)
            }
          },
          onAudioClick = {
            CommunicationActions.startVoiceCall(requireActivity(), state.recipient)
          },
          onMuteClick = {
            if (!state.buttonStripState.isMuted) {
              MuteDialog.show(requireContext(), viewModel::setMuteUntil)
            } else {
              MaterialAlertDialogBuilder(requireContext())
                .setMessage(state.recipient.muteUntil.formatMutedUntil(requireContext()))
                .setPositiveButton(R.string.ConversationSettingsFragment__unmute) { dialog, _ ->
                  viewModel.unmute()
                  dialog.dismiss()
                }
                .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
                .show()
            }
          },
          onSearchClick = {
            val intent = ConversationIntents.createBuilder(requireContext(), state.recipient.id, state.threadId)
              .withSearchOpen(true)
              .build()

            startActivity(intent)
            requireActivity().finish()
          }
        )
      )

      dividerPref()

      val summary = DSLSettingsText.from(formatDisappearingMessagesLifespan(state.disappearingMessagesLifespan))
      val icon = if (state.disappearingMessagesLifespan <= 0) {
        R.drawable.ic_update_timer_disabled_16
      } else {
        R.drawable.ic_update_timer_16
      }

      var enabled = true
      state.withGroupSettingsState {
        enabled = it.canEditGroupAttributes
      }

      clickPref(
        title = DSLSettingsText.from(R.string.ConversationSettingsFragment__disappearing_messages),
        summary = summary,
        icon = DSLSettingsIcon.from(icon),
        isEnabled = enabled,
        onClick = {
          val action = ConversationSettingsFragmentDirections.actionConversationSettingsFragmentToAppSettingsExpireTimer()
            .setInitialValue(state.disappearingMessagesLifespan)
            .setRecipientId(state.recipient.id)
            .setForResultMode(false)

          navController.navigate(action)
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__chat_color_and_wallpaper),
        icon = DSLSettingsIcon.from(R.drawable.ic_color_24),
        onClick = {
          startActivity(ChatWallpaperActivity.createIntent(requireContext(), state.recipient.id))
        }
      )

      if (!state.recipient.isSelf) {
        clickPref(
          title = DSLSettingsText.from(R.string.ConversationSettingsFragment__sounds_and_notifications),
          icon = DSLSettingsIcon.from(R.drawable.ic_speaker_24),
          onClick = {
            val action = ConversationSettingsFragmentDirections.actionConversationSettingsFragmentToSoundsAndNotificationsSettingsFragment(state.recipient.id)

            navController.navigate(action)
          }
        )
      }

      state.withRecipientSettingsState { recipientState ->
        when (recipientState.contactLinkState) {
          ContactLinkState.OPEN -> {
            @Suppress("DEPRECATION")
            clickPref(
              title = DSLSettingsText.from(R.string.ConversationSettingsFragment__contact_details),
              icon = DSLSettingsIcon.from(R.drawable.ic_profile_circle_24),
              onClick = {
                startActivityForResult(Intent(Intent.ACTION_VIEW, state.recipient.contactUri), REQUEST_CODE_VIEW_CONTACT)
              }
            )
          }

          ContactLinkState.ADD -> {
            @Suppress("DEPRECATION")
            clickPref(
              title = DSLSettingsText.from(R.string.ConversationSettingsFragment__add_as_a_contact),
              icon = DSLSettingsIcon.from(R.drawable.ic_plus_24),
              onClick = {
                startActivityForResult(RecipientExporter.export(state.recipient).asAddContactIntent(), REQUEST_CODE_ADD_CONTACT)
              }
            )
          }

          ContactLinkState.NONE -> {
          }
        }

        if (recipientState.identityRecord != null) {
          clickPref(
            title = DSLSettingsText.from(R.string.ConversationSettingsFragment__view_safety_number),
            icon = DSLSettingsIcon.from(R.drawable.ic_safety_number_24),
            onClick = {
              startActivity(VerifyIdentityActivity.newIntent(requireActivity(), recipientState.identityRecord))
            }
          )
        }
      }

      if (state.sharedMedia != null && state.sharedMedia.count > 0) {
        dividerPref()

        sectionHeaderPref(R.string.recipient_preference_activity__shared_media)

        @Suppress("DEPRECATION")
        customPref(
          SharedMediaPreference.Model(
            mediaCursor = state.sharedMedia,
            mediaIds = state.sharedMediaIds,
            onMediaRecordClick = { mediaRecord, isLtr ->
              startActivityForResult(
                MediaPreviewActivity.intentFromMediaRecord(requireContext(), mediaRecord, isLtr),
                REQUEST_CODE_RETURN_FROM_MEDIA
              )
            }
          )
        )

        clickPref(
          title = DSLSettingsText.from(R.string.ConversationSettingsFragment__see_all),
          onClick = {
            startActivity(MediaOverviewActivity.forThread(requireContext(), state.threadId))
          }
        )
      }

      state.withRecipientSettingsState { recipientSettingsState ->
        if (state.recipient.badges.isNotEmpty()) {
          dividerPref()

          sectionHeaderPref(R.string.ManageProfileFragment_badges)

          displayBadges(requireContext(), state.recipient.badges)
        }

        if (recipientSettingsState.selfHasGroups) {

          dividerPref()

          val groupsInCommonCount = recipientSettingsState.allGroupsInCommon.size
          sectionHeaderPref(
            DSLSettingsText.from(
              if (groupsInCommonCount == 0) {
                getString(R.string.ManageRecipientActivity_no_groups_in_common)
              } else {
                resources.getQuantityString(
                  R.plurals.ManageRecipientActivity_d_groups_in_common,
                  groupsInCommonCount,
                  groupsInCommonCount
                )
              }
            )
          )

          customPref(
            LargeIconClickPreference.Model(
              title = DSLSettingsText.from(R.string.ConversationSettingsFragment__add_to_a_group),
              icon = DSLSettingsIcon.from(R.drawable.add_to_a_group, NO_TINT),
              onClick = {
                viewModel.onAddToGroup()
              }
            )
          )

          for (group in recipientSettingsState.groupsInCommon) {
            customPref(
              RecipientPreference.Model(
                recipient = group,
                onClick = {
                  CommunicationActions.startConversation(requireActivity(), group, null)
                  requireActivity().finish()
                }
              )
            )
          }

          if (recipientSettingsState.canShowMoreGroupsInCommon) {
            customPref(
              LargeIconClickPreference.Model(
                title = DSLSettingsText.from(R.string.ConversationSettingsFragment__see_all),
                icon = DSLSettingsIcon.from(R.drawable.show_more, NO_TINT),
                onClick = {
                  viewModel.revealAllMembers()
                }
              )
            )
          }
        }
      }

      state.withGroupSettingsState { groupState ->
        val memberCount = groupState.allMembers.size

        if (groupState.canAddToGroup || memberCount > 0) {
          dividerPref()

          sectionHeaderPref(DSLSettingsText.from(resources.getQuantityString(R.plurals.ContactSelectionListFragment_d_members, memberCount, memberCount)))
        }

        if (groupState.canAddToGroup) {
          customPref(
            LargeIconClickPreference.Model(
              title = DSLSettingsText.from(R.string.ConversationSettingsFragment__add_members),
              icon = DSLSettingsIcon.from(R.drawable.add_to_a_group, NO_TINT),
              onClick = {
                viewModel.onAddToGroup()
              }
            )
          )
        }

        for (member in groupState.members) {
          customPref(
            RecipientPreference.Model(
              recipient = member.member,
              isAdmin = member.isAdmin,
              onClick = {
                RecipientBottomSheetDialogFragment.create(member.member.id, groupState.groupId).show(parentFragmentManager, "BOTTOM")
              }
            )
          )
        }

        if (groupState.canShowMoreGroupMembers) {
          customPref(
            LargeIconClickPreference.Model(
              title = DSLSettingsText.from(R.string.ConversationSettingsFragment__see_all),
              icon = DSLSettingsIcon.from(R.drawable.show_more, NO_TINT),
              onClick = {
                viewModel.revealAllMembers()
              }
            )
          )
        }

        if (state.recipient.isPushV2Group) {
          dividerPref()

          clickPref(
            title = DSLSettingsText.from(R.string.ConversationSettingsFragment__group_link),
            summary = DSLSettingsText.from(if (groupState.groupLinkEnabled) R.string.preferences_on else R.string.preferences_off),
            icon = DSLSettingsIcon.from(R.drawable.ic_link_16),
            onClick = {
              ShareableGroupLinkDialogFragment.create(groupState.groupId.requireV2()).show(parentFragmentManager, "DIALOG")
            }
          )

          clickPref(
            title = DSLSettingsText.from(R.string.ConversationSettingsFragment__requests_and_invites),
            icon = DSLSettingsIcon.from(R.drawable.ic_update_group_add_16),
            onClick = {
              startActivity(ManagePendingAndRequestingMembersActivity.newIntent(requireContext(), groupState.groupId.requireV2()))
            }
          )

          if (groupState.isSelfAdmin) {
            clickPref(
              title = DSLSettingsText.from(R.string.ConversationSettingsFragment__permissions),
              icon = DSLSettingsIcon.from(R.drawable.ic_lock_24),
              onClick = {
                val action = ConversationSettingsFragmentDirections.actionConversationSettingsFragmentToPermissionsSettingsFragment(ParcelableGroupId.from(groupState.groupId))
                navController.navigate(action)
              }
            )
          }
        }

        if (groupState.canLeave) {
          dividerPref()

          clickPref(
            title = DSLSettingsText.from(R.string.conversation__menu_leave_group, alertTint),
            icon = DSLSettingsIcon.from(leaveIcon),
            onClick = {
              LeaveGroupDialog.handleLeavePushGroup(requireActivity(), groupState.groupId.requirePush(), null)
            }
          )
        }
      }

      if (state.canModifyBlockedState) {
        state.withRecipientSettingsState {
          dividerPref()
        }

        state.withGroupSettingsState {
          if (!it.canLeave) {
            dividerPref()
          }
        }

        val isBlocked = state.recipient.isBlocked
        val isGroup = state.recipient.isPushGroup

        val title = when {
          isBlocked && isGroup -> R.string.ConversationSettingsFragment__unblock_group
          isBlocked -> R.string.ConversationSettingsFragment__unblock
          isGroup -> R.string.ConversationSettingsFragment__block_group
          else -> R.string.ConversationSettingsFragment__block
        }

        val titleTint = if (isBlocked) null else alertTint
        val blockUnblockIcon = if (isBlocked) unblockIcon else blockIcon

        clickPref(
          title = if (titleTint != null) DSLSettingsText.from(title, titleTint) else DSLSettingsText.from(title),
          icon = DSLSettingsIcon.from(blockUnblockIcon),
          onClick = {
            if (state.recipient.isBlocked) {
              BlockUnblockDialog.showUnblockFor(requireContext(), viewLifecycleOwner.lifecycle, state.recipient) {
                viewModel.unblock()
              }
            } else {
              BlockUnblockDialog.showBlockFor(requireContext(), viewLifecycleOwner.lifecycle, state.recipient) {
                viewModel.block()
              }
            }
          }
        )
      }
    }
  }

  private fun formatDisappearingMessagesLifespan(disappearingMessagesLifespan: Int): String {
    return if (disappearingMessagesLifespan <= 0) {
      getString(R.string.preferences_off)
    } else {
      ExpirationUtil.getExpirationDisplayValue(requireContext(), disappearingMessagesLifespan)
    }
  }

  private fun handleAddToAGroup(addToAGroup: ConversationSettingsEvent.AddToAGroup) {
    startActivity(AddToGroupsActivity.newIntent(requireContext(), addToAGroup.recipientId, addToAGroup.groupMembership))
  }

  @Suppress("DEPRECATION")
  private fun handleAddMembersToGroup(addMembersToGroup: ConversationSettingsEvent.AddMembersToGroup) {
    startActivityForResult(
      AddMembersActivity.createIntent(
        requireContext(),
        addMembersToGroup.groupId,
        ContactsCursorLoader.DisplayMode.FLAG_PUSH,
        addMembersToGroup.selectionWarning,
        addMembersToGroup.selectionLimit,
        addMembersToGroup.isAnnouncementGroup,
        addMembersToGroup.groupMembersWithoutSelf
      ),
      REQUEST_CODE_ADD_MEMBERS_TO_GROUP
    )
  }

  private fun showGroupHardLimitDialog() {
    GroupLimitDialog.showHardLimitMessage(requireContext())
  }

  private fun showAddMembersToGroupError(showAddMembersToGroupError: ConversationSettingsEvent.ShowAddMembersToGroupError) {
    Toast.makeText(requireContext(), GroupErrors.getUserDisplayMessage(showAddMembersToGroupError.failureReason), Toast.LENGTH_LONG).show()
  }

  private fun showGroupInvitesSentDialog(showGroupInvitesSentDialog: ConversationSettingsEvent.ShowGroupInvitesSentDialog) {
    GroupInviteSentDialog.showInvitesSent(requireContext(), viewLifecycleOwner, showGroupInvitesSentDialog.invitesSentTo)
  }

  private fun showMembersAdded(showMembersAdded: ConversationSettingsEvent.ShowMembersAdded) {
    val string = resources.getQuantityString(
      R.plurals.ManageGroupActivity_added,
      showMembersAdded.membersAddedCount,
      showMembersAdded.membersAddedCount
    )

    Snackbar.make(requireView(), string, Snackbar.LENGTH_SHORT).setTextColor(Color.WHITE).show()
  }

  private class ConversationSettingsOnUserScrolledAnimationHelper(
    private val toolbarAvatar: View,
    private val toolbarTitle: View,
    private val toolbarBackground: View,
    toolbarShadow: View
  ) : ToolbarShadowAnimationHelper(toolbarShadow) {

    override val duration: Long = 200L

    private val actionBarSize = ThemeUtil.getThemedDimen(toolbarShadow.context, R.attr.actionBarSize)
    private val rect = Rect()

    override fun getAnimationState(recyclerView: RecyclerView): AnimationState {
      val layoutManager = recyclerView.layoutManager as FlexboxLayoutManager

      return if (layoutManager.findFirstVisibleItemPosition() == 0) {
        val firstChild = requireNotNull(layoutManager.getChildAt(0))
        firstChild.getLocalVisibleRect(rect)

        if (rect.height() <= actionBarSize) {
          AnimationState.SHOW
        } else {
          AnimationState.HIDE
        }
      } else {
        AnimationState.SHOW
      }
    }

    override fun show(duration: Long) {
      super.show(duration)

      toolbarAvatar
        .animate()
        .setDuration(duration)
        .translationY(0f)
        .alpha(1f)

      toolbarTitle
        .animate()
        .setDuration(duration)
        .translationY(0f)
        .alpha(1f)

      toolbarBackground
        .animate()
        .setDuration(duration)
        .alpha(1f)
    }

    override fun hide(duration: Long) {
      super.hide(duration)

      toolbarAvatar
        .animate()
        .setDuration(duration)
        .translationY(ViewUtil.dpToPx(56).toFloat())
        .alpha(0f)

      toolbarTitle
        .animate()
        .setDuration(duration)
        .translationY(ViewUtil.dpToPx(56).toFloat())
        .alpha(0f)

      toolbarBackground
        .animate()
        .setDuration(duration)
        .alpha(0f)
    }
  }

  interface Callback {
    fun onContentWillRender()
  }
}