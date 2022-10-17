package su.sres.securesms.conversation;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.GroupDatabase;
import su.sres.securesms.database.GroupDatabase.GroupRecord;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.groups.GroupChangeBusyException;
import su.sres.securesms.groups.GroupChangeFailedException;
import su.sres.securesms.groups.GroupManager;
import su.sres.securesms.groups.ui.GroupChangeFailureReason;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.util.AsynchronousCallback;
import su.sres.securesms.util.concurrent.SignalExecutors;
import su.sres.securesms.util.livedata.LiveDataUtil;

import java.io.IOException;

final class ConversationGroupViewModel extends ViewModel {

    private final MutableLiveData<Recipient>          liveRecipient;
    private final LiveData<GroupActiveState>          groupActiveState;
    private final LiveData<GroupDatabase.MemberLevel> selfMembershipLevel;

    private ConversationGroupViewModel() {
        this.liveRecipient = new MutableLiveData<>();

        LiveData<GroupRecord> groupRecord = LiveDataUtil.mapAsync(liveRecipient, ConversationGroupViewModel::getGroupRecordForRecipient);

        this.groupActiveState    = Transformations.distinctUntilChanged(Transformations.map(groupRecord, ConversationGroupViewModel::mapToGroupActiveState));
        this.selfMembershipLevel = Transformations.distinctUntilChanged(Transformations.map(groupRecord, ConversationGroupViewModel::mapToSelfMembershipLevel));
    }

    void onRecipientChange(Recipient recipient) {
        liveRecipient.setValue(recipient);
    }

    LiveData<GroupActiveState> getGroupActiveState() {
        return groupActiveState;
    }

    LiveData<GroupDatabase.MemberLevel> getSelfMemberLevel() {
        return selfMembershipLevel;
    }

    private static @Nullable GroupRecord getGroupRecordForRecipient(@Nullable Recipient recipient) {
        if (recipient != null && recipient.isGroup()) {
            Application context         = ApplicationDependencies.getApplication();
            GroupDatabase groupDatabase = DatabaseFactory.getGroupDatabase(context);
            return groupDatabase.getGroup(recipient.getId()).orNull();
        } else {
            return null;
        }
    }

    private static GroupActiveState mapToGroupActiveState(@Nullable GroupRecord record) {
        if (record == null) {
            return null;
        }
        return new GroupActiveState(record.isActive(), record.isV2Group());
    }

    private static GroupDatabase.MemberLevel mapToSelfMembershipLevel(@Nullable GroupRecord record) {
        if (record == null) {
            return null;
        }
        return record.memberLevel(Recipient.self());
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

    static class Factory extends ViewModelProvider.NewInstanceFactory {
        @Override
        public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            //noinspection ConstantConditions
            return modelClass.cast(new ConversationGroupViewModel());
        }
    }
}