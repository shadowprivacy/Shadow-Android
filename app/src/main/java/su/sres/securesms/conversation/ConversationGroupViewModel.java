package su.sres.securesms.conversation;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.annimon.stream.Stream;

import su.sres.securesms.database.GroupDatabase;
import su.sres.securesms.database.GroupDatabase.GroupRecord;
import su.sres.securesms.database.ShadowDatabase;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.groups.GroupChangeBusyException;
import su.sres.securesms.groups.GroupChangeFailedException;
import su.sres.securesms.groups.GroupId;
import su.sres.securesms.groups.GroupManager;
import su.sres.securesms.groups.GroupsV1MigrationUtil;
import su.sres.securesms.groups.ui.GroupChangeFailureReason;
import su.sres.securesms.groups.ui.invitesandrequests.invite.GroupLinkInviteFriendsBottomSheetDialogFragment;
import su.sres.securesms.profiles.spoofing.ReviewRecipient;
import su.sres.securesms.profiles.spoofing.ReviewUtil;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.util.AsynchronousCallback;
import su.sres.core.util.concurrent.SignalExecutors;
import su.sres.securesms.util.concurrent.SimpleTask;
import su.sres.securesms.util.livedata.LiveDataUtil;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

final class ConversationGroupViewModel extends ViewModel {

  private final MutableLiveData<Recipient>        liveRecipient;
  private final LiveData<GroupActiveState>        groupActiveState;
  private final LiveData<ConversationMemberLevel> selfMembershipLevel;
  private final LiveData<Integer>                 actionableRequestingMembers;
  private final LiveData<ReviewState>             reviewState;
  private final LiveData<List<RecipientId>>       gv1MigrationSuggestions;

  private boolean firstTimeInviteFriendsTriggered;

  private ConversationGroupViewModel() {
    this.liveRecipient = new MutableLiveData<>();

    LiveData<GroupRecord> groupRecord = LiveDataUtil.mapAsync(liveRecipient, ConversationGroupViewModel::getGroupRecordForRecipient);
    LiveData<List<Recipient>> duplicates = LiveDataUtil.mapAsync(groupRecord, record -> {
      if (record != null && record.isV2Group()) {
        return Stream.of(ReviewUtil.getDuplicatedRecipients(record.getId().requireV2()))
                     .map(ReviewRecipient::getRecipient)
                     .toList();
      } else {
        return Collections.emptyList();
      }
    });

    this.groupActiveState            = Transformations.distinctUntilChanged(Transformations.map(groupRecord, ConversationGroupViewModel::mapToGroupActiveState));
    this.selfMembershipLevel         = Transformations.distinctUntilChanged(Transformations.map(groupRecord, ConversationGroupViewModel::mapToSelfMembershipLevel));
    this.actionableRequestingMembers = Transformations.distinctUntilChanged(Transformations.map(groupRecord, ConversationGroupViewModel::mapToActionableRequestingMemberCount));
    this.gv1MigrationSuggestions     = Transformations.distinctUntilChanged(LiveDataUtil.mapAsync(groupRecord, ConversationGroupViewModel::mapToGroupV1MigrationSuggestions));
    this.reviewState                 = LiveDataUtil.combineLatest(groupRecord,
                                                                  duplicates,
                                                                  (record, dups) -> dups.isEmpty()
                                                                                    ? ReviewState.EMPTY
                                                                                    : new ReviewState(record.getId().requireV2(), dups.get(0), dups.size()));
  }

  void onRecipientChange(Recipient recipient) {
    liveRecipient.setValue(recipient);
  }

  void onSuggestedMembersBannerDismissed(@NonNull GroupId groupId, @NonNull List<RecipientId> suggestions) {
    SignalExecutors.BOUNDED.execute(() -> {
      if (groupId.isV2()) {
        ShadowDatabase.groups().removeUnmigratedV1Members(groupId.requireV2(), suggestions);
        liveRecipient.postValue(liveRecipient.getValue());
      }
    });
  }

  /**
   * The number of pending group join requests that can be actioned by this client.
   */
  LiveData<Integer> getActionableRequestingMembers() {
    return actionableRequestingMembers;
  }

  LiveData<GroupActiveState> getGroupActiveState() {
    return groupActiveState;
  }

  LiveData<ConversationMemberLevel> getSelfMemberLevel() {
    return selfMembershipLevel;
  }

  public LiveData<ReviewState> getReviewState() {
    return reviewState;
  }

  @NonNull LiveData<List<RecipientId>> getGroupV1MigrationSuggestions() {
    return gv1MigrationSuggestions;
  }

  boolean isNonAdminInAnnouncementGroup() {
    ConversationMemberLevel level = selfMembershipLevel.getValue();
    return level != null && level.getMemberLevel() != GroupDatabase.MemberLevel.ADMINISTRATOR && level.isAnnouncementGroup();
  }

  private static @Nullable GroupRecord getGroupRecordForRecipient(@Nullable Recipient recipient) {
    if (recipient != null && recipient.isGroup()) {
      Application   context       = ApplicationDependencies.getApplication();
      GroupDatabase groupDatabase = ShadowDatabase.groups();
      return groupDatabase.getGroup(recipient.getId()).orNull();
    } else {
      return null;
    }
  }

  private static int mapToActionableRequestingMemberCount(@Nullable GroupRecord record) {
    if (record != null &&
        record.isV2Group() &&
        record.memberLevel(Recipient.self()) == GroupDatabase.MemberLevel.ADMINISTRATOR)
    {
      return record.requireV2GroupProperties()
                   .getDecryptedGroup()
                   .getRequestingMembersCount();
    } else {
      return 0;
    }
  }

  private static GroupActiveState mapToGroupActiveState(@Nullable GroupRecord record) {
    if (record == null) {
      return null;
    }
    return new GroupActiveState(record.isActive(), record.isV2Group());
  }

  private static ConversationMemberLevel mapToSelfMembershipLevel(@Nullable GroupRecord record) {
    if (record == null) {
      return null;
    }
    return new ConversationMemberLevel(record.memberLevel(Recipient.self()), record.isAnnouncementGroup());
  }

  @WorkerThread
  private static List<RecipientId> mapToGroupV1MigrationSuggestions(@Nullable GroupRecord record) {
    if (record == null) {
      return Collections.emptyList();
    }

    if (!record.isV2Group()) {
      return Collections.emptyList();
    }

    if (!record.isActive() || record.isPendingMember(Recipient.self())) {
      return Collections.emptyList();
    }

    return Stream.of(record.getUnmigratedV1Members())
                 .filterNot(m -> record.getMembers().contains(m))
                 .map(Recipient::resolved)
                 .filter(GroupsV1MigrationUtil::isAutoMigratable)
                 .map(Recipient::getId)
                 .toList();
  }

  public static void onCancelJoinRequest(@NonNull Recipient recipient,
                                         @NonNull AsynchronousCallback.WorkerThread<Void, GroupChangeFailureReason> callback)
  {
    SignalExecutors.UNBOUNDED.execute(() -> {
      if (!recipient.isPushV2Group()) {
        throw new AssertionError();
      }

      try {
        GroupManager.cancelJoinRequest(ApplicationDependencies.getApplication(), recipient.getGroupId().get().requireV2());
        callback.onComplete(null);
      } catch (GroupChangeFailedException | GroupChangeBusyException | IOException e) {
        callback.onError(GroupChangeFailureReason.fromException(e));
      }
    });
  }

  void inviteFriendsOneTimeIfJustSelfInGroup(@NonNull FragmentManager supportFragmentManager, @NonNull GroupId.V2 groupId) {
    if (firstTimeInviteFriendsTriggered) {
      return;
    }

    firstTimeInviteFriendsTriggered = true;

    SimpleTask.run(() -> ShadowDatabase.groups()
                                        .requireGroup(groupId)
                                        .getMembers().equals(Collections.singletonList(Recipient.self().getId())),
                   justSelf -> {
                     if (justSelf) {
                       inviteFriends(supportFragmentManager, groupId);
                     }
                   }
    );
  }

  void inviteFriends(@NonNull FragmentManager supportFragmentManager, @NonNull GroupId.V2 groupId) {
    GroupLinkInviteFriendsBottomSheetDialogFragment.show(supportFragmentManager, groupId);
  }

  static final class ReviewState {

    private static final ReviewState EMPTY = new ReviewState(null, Recipient.UNKNOWN, 0);

    private final GroupId.V2 groupId;
    private final Recipient  recipient;
    private final int        count;

    ReviewState(@Nullable GroupId.V2 groupId, @NonNull Recipient recipient, int count) {
      this.groupId   = groupId;
      this.recipient = recipient;
      this.count     = count;
    }

    public @Nullable GroupId.V2 getGroupId() {
      return groupId;
    }

    public @NonNull Recipient getRecipient() {
      return recipient;
    }

    public int getCount() {
      return count;
    }
  }

  static final class GroupActiveState {
    private final boolean isActive;
    private final boolean isActiveV2;

    public GroupActiveState(boolean isActive, boolean isV2) {
      this.isActive   = isActive;
      this.isActiveV2 = isActive && isV2;
    }

    public boolean isActiveGroup() {
      return isActive;
    }

    public boolean isActiveV2Group() {
      return isActiveV2;
    }
  }

  static final class ConversationMemberLevel {
    private final GroupDatabase.MemberLevel memberLevel;
    private final boolean                   isAnnouncementGroup;

    private ConversationMemberLevel(GroupDatabase.MemberLevel memberLevel, boolean isAnnouncementGroup) {
      this.memberLevel         = memberLevel;
      this.isAnnouncementGroup = isAnnouncementGroup;
    }

    public @NonNull GroupDatabase.MemberLevel getMemberLevel() {
      return memberLevel;
    }

    public boolean isAnnouncementGroup() {
      return isAnnouncementGroup;
    }
  }

  static class Factory extends ViewModelProvider.NewInstanceFactory {
    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection ConstantConditions
      return modelClass.cast(new ConversationGroupViewModel());
    }
  }
}