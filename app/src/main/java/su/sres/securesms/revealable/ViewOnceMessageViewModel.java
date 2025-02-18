package su.sres.securesms.revealable;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import su.sres.core.util.ThreadUtil;
import su.sres.securesms.database.DatabaseObserver;
import su.sres.securesms.database.model.MmsMessageRecord;
import su.sres.core.util.logging.Log;
import su.sres.securesms.dependencies.ApplicationDependencies;

import org.whispersystems.libsignal.util.guava.Optional;

class ViewOnceMessageViewModel extends ViewModel {

  private static final String TAG = Log.tag(ViewOnceMessageViewModel.class);

  private final MutableLiveData<Optional<MmsMessageRecord>> message;
  private final DatabaseObserver.Observer                   observer;

  private ViewOnceMessageViewModel(long messageId, @NonNull ViewOnceMessageRepository repository) {
    this.message  = new MutableLiveData<>();
    this.observer = () -> repository.getMessage(messageId, this::onMessageRetrieved);

    repository.getMessage(messageId, message -> {
      if (message.isPresent()) {
        ApplicationDependencies.getDatabaseObserver().registerConversationObserver(message.get().getThreadId(), observer);
      }

      onMessageRetrieved(message);
    });
  }

  @NonNull LiveData<Optional<MmsMessageRecord>> getMessage() {
    return message;
  }

  @Override
  protected void onCleared() {
    ApplicationDependencies.getDatabaseObserver().unregisterObserver(observer);
  }

  private void onMessageRetrieved(@NonNull Optional<MmsMessageRecord> optionalMessage) {
    ThreadUtil.runOnMain(() -> {
      MmsMessageRecord current  = message.getValue() != null ? message.getValue().orNull() : null;
      MmsMessageRecord proposed = optionalMessage.orNull();

      if (current != null && proposed != null && current.getId() == proposed.getId()) {
        Log.d(TAG, "Same ID -- skipping update");
      } else {
        message.setValue(optionalMessage);
      }
    });
  }

  static class Factory extends ViewModelProvider.NewInstanceFactory {

    private final long                      messageId;
    private final ViewOnceMessageRepository repository;

    Factory(long messageId, @NonNull ViewOnceMessageRepository repository) {
      this.messageId  = messageId;
      this.repository = repository;
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection ConstantConditions
      return modelClass.cast(new ViewOnceMessageViewModel(messageId, repository));
    }
  }
}