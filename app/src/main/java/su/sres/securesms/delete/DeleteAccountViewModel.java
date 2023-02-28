package su.sres.securesms.delete;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.util.DefaultValueLiveData;
import su.sres.securesms.util.SingleLiveEvent;

public class DeleteAccountViewModel extends ViewModel {

    private final DeleteAccountRepository    repository;
    private final SingleLiveEvent<EventType> events;
    private final MutableLiveData<String>  userLogin;

    public DeleteAccountViewModel(@NonNull DeleteAccountRepository repository) {
        this.repository      = repository;
        this.userLogin       = new MutableLiveData<>();
        this.events          = new SingleLiveEvent<>();
    }

    @Nullable String getUserLogin() {
        return userLogin.getValue();
    }

    @NonNull SingleLiveEvent<EventType> getEvents() {
        return events;
    }

    void deleteAccount() {
        repository.deleteAccount(() -> events.postValue(EventType.SERVER_DELETION_FAILED),
                () -> events.postValue(EventType.LOCAL_DATA_DELETION_FAILED));
    }

    void submit() {

        String userLogin = this.userLogin.getValue();

        if (Recipient.self().requireE164().equals(userLogin)) {
            events.setValue(EventType.CONFIRM_DELETION);
        } else {
            events.setValue(EventType.NOT_A_MATCH);
        }
    }

    void setUserLogin(String userLogin) {
        this.userLogin.setValue(userLogin);
    }

    enum EventType {
        NOT_A_MATCH,
        CONFIRM_DELETION,
        SERVER_DELETION_FAILED,
        LOCAL_DATA_DELETION_FAILED
    }

    public static final class Factory implements ViewModelProvider.Factory {

        private final DeleteAccountRepository repository;

        public Factory(DeleteAccountRepository repository) {
            this.repository = repository;
        }

        @Override
        public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            return modelClass.cast(new DeleteAccountViewModel(repository));
        }
    }
}
