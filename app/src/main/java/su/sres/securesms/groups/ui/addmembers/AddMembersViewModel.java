package su.sres.securesms.groups.ui.addmembers;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.util.Consumer;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import su.sres.securesms.R;
import su.sres.securesms.contacts.SelectedContact;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.groups.GroupId;
import su.sres.securesms.groups.LiveGroup;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.util.DefaultValueLiveData;
import su.sres.securesms.util.concurrent.SimpleTask;
import su.sres.securesms.util.livedata.LiveDataUtil;
import org.whispersystems.libsignal.util.guava.Preconditions;

import java.util.List;
import java.util.Objects;

public final class AddMembersViewModel extends ViewModel {

    private final AddMembersRepository repository;

    private AddMembersViewModel(@NonNull GroupId groupId) {
        this.repository = new AddMembersRepository(groupId);
    }

    void getDialogStateForSelectedContacts(@NonNull List<SelectedContact> selectedContacts,
                                           @NonNull Consumer<AddMemberDialogMessageState> callback)
    {
        SimpleTask.run(
                () -> {
                    AddMemberDialogMessageStatePartial partialState = selectedContacts.size() == 1 ? getDialogStateForSingleRecipient(selectedContacts.get(0))
                            : getDialogStateForMultipleRecipients(selectedContacts.size());

                    return new AddMemberDialogMessageState(partialState.recipientId == null ? Recipient.UNKNOWN : Recipient.resolved(partialState.recipientId),
                            partialState.memberCount, titleOrDefault(repository.getGroupTitle()));
                },
                callback::accept
        );
    }

    @WorkerThread
    private AddMemberDialogMessageStatePartial getDialogStateForSingleRecipient(@NonNull SelectedContact selectedContact) {
        return new AddMemberDialogMessageStatePartial(repository.getOrCreateRecipientId(selectedContact));
    }

    private AddMemberDialogMessageStatePartial getDialogStateForMultipleRecipients(int recipientCount) {
        return new AddMemberDialogMessageStatePartial(recipientCount);
    }

    private static @NonNull String titleOrDefault(@Nullable String title) {
        return TextUtils.isEmpty(title) ? ApplicationDependencies.getApplication().getString(R.string.Recipient_unknown)
                : Objects.requireNonNull(title);
    }

    private static final class AddMemberDialogMessageStatePartial {
        private final RecipientId recipientId;
        private final int         memberCount;

        private AddMemberDialogMessageStatePartial(@NonNull RecipientId recipientId) {
            this.recipientId = recipientId;
            this.memberCount = 1;
        }

        private AddMemberDialogMessageStatePartial(int memberCount) {
            Preconditions.checkArgument(memberCount > 1);
            this.memberCount = memberCount;
            this.recipientId = null;
        }
    }

    public static final class AddMemberDialogMessageState {
        private final Recipient recipient;
        private final String    groupTitle;
        private final int       selectionCount;

        private AddMemberDialogMessageState(@Nullable Recipient recipient, int selectionCount, @NonNull String groupTitle) {
            this.recipient      = recipient;
            this.groupTitle     = groupTitle;
            this.selectionCount = selectionCount;
        }

        public Recipient getRecipient() {
            return recipient;
        }

        public int getSelectionCount() {
            return selectionCount;
        }

        public @NonNull String getGroupTitle() {
            return groupTitle;
        }
    }

    public static class Factory implements ViewModelProvider.Factory {

        private final GroupId groupId;

        public Factory(@NonNull GroupId groupId) {
            this.groupId = groupId;
        }

        @Override
        public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            return Objects.requireNonNull(modelClass.cast(new AddMembersViewModel(groupId)));
        }
    }
}