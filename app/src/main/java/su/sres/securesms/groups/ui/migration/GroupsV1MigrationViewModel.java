package su.sres.securesms.groups.ui.migration;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.util.concurrent.SignalExecutors;

import java.util.List;

class GroupsV1MigrationViewModel extends ViewModel {

    private final MutableLiveData<List<Recipient>> pendingMembers;

    private GroupsV1MigrationViewModel(@NonNull List<RecipientId> pendingMembers) {
        this.pendingMembers = new MutableLiveData<>();

        SignalExecutors.BOUNDED.execute(() -> {
            this.pendingMembers.postValue(Recipient.resolvedList(pendingMembers));
        });
    }

    @NonNull LiveData<List<Recipient>> getPendingMembers() {
        return pendingMembers;
    }

    static class Factory extends ViewModelProvider.NewInstanceFactory {

        private final List<RecipientId> pendingMembers;

        Factory(List<RecipientId> pendingMembers) {
            this.pendingMembers = pendingMembers;
        }

        @Override
        public @NonNull<T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            return modelClass.cast(new GroupsV1MigrationViewModel(pendingMembers));
        }
    }
}
