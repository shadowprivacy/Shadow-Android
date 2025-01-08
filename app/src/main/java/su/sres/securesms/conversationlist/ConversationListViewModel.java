package su.sres.securesms.conversationlist;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.LiveDataReactiveStreams;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import android.app.Application;

import androidx.annotation.NonNull;

import android.text.TextUtils;

import org.whispersystems.libsignal.util.guava.Optional;

import java.util.List;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import su.sres.paging.PagedData;
import su.sres.paging.PagingConfig;
import su.sres.paging.PagingController;
import su.sres.securesms.conversationlist.model.Conversation;
import su.sres.securesms.conversationlist.model.UnreadPayments;
import su.sres.securesms.conversationlist.model.UnreadPaymentsLiveData;
import su.sres.securesms.database.DatabaseObserver;
import su.sres.securesms.database.ShadowDatabase;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.core.util.logging.Log;
import su.sres.securesms.megaphone.Megaphone;
import su.sres.securesms.megaphone.MegaphoneRepository;
import su.sres.securesms.megaphone.Megaphones;
import su.sres.securesms.payments.UnreadPaymentsRepository;
import su.sres.securesms.search.SearchRepository;
import su.sres.securesms.search.SearchResult;
import su.sres.securesms.util.Debouncer;
import su.sres.securesms.util.ThrottledDebouncer;
import su.sres.securesms.util.livedata.LiveDataUtil;
import su.sres.securesms.util.paging.Invalidator;
import su.sres.signalservice.api.websocket.WebSocketConnectionState;

class ConversationListViewModel extends ViewModel {

  private static final String TAG = Log.tag(ConversationListViewModel.class);

  private static boolean coldStart = true;

  private final MutableLiveData<Megaphone>    megaphone;
  private final MutableLiveData<SearchResult> searchResult;
  private final PagedData<Long, Conversation> pagedData;
  private final LiveData<Boolean>             hasNoConversations;
  private final SearchRepository              searchRepository;
  private final MegaphoneRepository           megaphoneRepository;
  private final Debouncer                     messageSearchDebouncer;
  private final Debouncer                     contactSearchDebouncer;
  private final ThrottledDebouncer            updateDebouncer;
  private final DatabaseObserver.Observer     observer;
  private final Invalidator                   invalidator;
  private final UnreadPaymentsLiveData        unreadPaymentsLiveData;
  private final UnreadPaymentsRepository      unreadPaymentsRepository;

  private String       activeQuery;
  private SearchResult activeSearchResult;
  private int          pinnedCount;

  private ConversationListViewModel(@NonNull Application application, @NonNull SearchRepository searchRepository, boolean isArchived) {
    this.megaphone                = new MutableLiveData<>();
    this.searchResult             = new MutableLiveData<>();
    this.searchRepository         = searchRepository;
    this.megaphoneRepository      = ApplicationDependencies.getMegaphoneRepository();
    this.unreadPaymentsRepository = new UnreadPaymentsRepository();
    this.messageSearchDebouncer   = new Debouncer(500);
    this.contactSearchDebouncer   = new Debouncer(100);
    this.updateDebouncer          = new ThrottledDebouncer(500);
    this.activeSearchResult       = SearchResult.EMPTY;
    this.invalidator              = new Invalidator();
    this.pagedData                = PagedData.create(ConversationListDataSource.create(application, isArchived),
                                                     new PagingConfig.Builder()
                                                         .setPageSize(15)
                                                         .setBufferPages(2)
                                                         .build());
    this.unreadPaymentsLiveData   = new UnreadPaymentsLiveData();
    this.observer                 = () -> {
      updateDebouncer.publish(() -> {
        if (!TextUtils.isEmpty(activeQuery)) {
          onSearchQueryUpdated(activeQuery);
        }
        pagedData.getController().onDataInvalidated();
      });
    };

    this.hasNoConversations = LiveDataUtil.mapAsync(pagedData.getData(), conversations -> {
      pinnedCount = ShadowDatabase.threads().getPinnedConversationListCount();

      if (conversations.size() > 0) {
        return false;
      } else {
        return ShadowDatabase.threads().getArchivedConversationListCount() == 0;
      }
    });

    ApplicationDependencies.getDatabaseObserver().registerConversationListObserver(observer);
  }

  public LiveData<Boolean> hasNoConversations() {
    return hasNoConversations;
  }

  @NonNull LiveData<SearchResult> getSearchResult() {
    return searchResult;
  }

  @NonNull LiveData<Megaphone> getMegaphone() {
    return megaphone;
  }

  @NonNull LiveData<List<Conversation>> getConversationList() {
    return pagedData.getData();
  }

  @NonNull
  PagingController getPagingController() {
    return pagedData.getController();
  }

  @NonNull LiveData<WebSocketConnectionState> getPipeState() {
    return LiveDataReactiveStreams.fromPublisher(ApplicationDependencies.getSignalWebSocket().getWebSocketState().toFlowable(BackpressureStrategy.LATEST));
  }

  @NonNull LiveData<Optional<UnreadPayments>> getUnreadPaymentsLiveData() {
    return unreadPaymentsLiveData;
  }

  public int getPinnedCount() {
    return pinnedCount;
  }

  void onVisible() {
    megaphoneRepository.getNextMegaphone(megaphone::postValue);
    if (!coldStart) {
      ApplicationDependencies.getDatabaseObserver().notifyConversationListListeners();
    }

    coldStart = false;
  }

  void onMegaphoneCompleted(@NonNull Megaphones.Event event) {
    megaphone.postValue(null);
    megaphoneRepository.markFinished(event);
  }

  void onMegaphoneSnoozed(@NonNull Megaphones.Event event) {
    megaphoneRepository.markSeen(event);
    megaphone.postValue(null);
  }

  void onMegaphoneVisible(@NonNull Megaphone visible) {
    megaphoneRepository.markVisible(visible.getEvent());
  }

  void onUnreadPaymentsClosed() {
    unreadPaymentsRepository.markAllPaymentsSeen();
  }

  void onSearchQueryUpdated(String query) {
    activeQuery = query;

    contactSearchDebouncer.publish(() -> {
      searchRepository.queryThreads(query, result -> {
        if (!result.getQuery().equals(activeQuery)) {
          return;
        }

        if (!activeSearchResult.getQuery().equals(activeQuery)) {
          activeSearchResult = SearchResult.EMPTY;
        }

        activeSearchResult = activeSearchResult.merge(result);
        searchResult.postValue(activeSearchResult);
      });
      searchRepository.queryContacts(query, result -> {
        if (!result.getQuery().equals(activeQuery)) {
          return;
        }

        if (!activeSearchResult.getQuery().equals(activeQuery)) {
          activeSearchResult = SearchResult.EMPTY;
        }

        activeSearchResult = activeSearchResult.merge(result);
        searchResult.postValue(activeSearchResult);
      });
    });

    messageSearchDebouncer.publish(() -> {
      searchRepository.queryMessages(query, result -> {
        if (!result.getQuery().equals(activeQuery)) {
          return;
        }

        if (!activeSearchResult.getQuery().equals(activeQuery)) {
          activeSearchResult = SearchResult.EMPTY;
        }

        activeSearchResult = activeSearchResult.merge(result);
        searchResult.postValue(activeSearchResult);
      });
    });
  }

  @Override
  protected void onCleared() {
    invalidator.invalidate();
    messageSearchDebouncer.clear();
    updateDebouncer.clear();
    ApplicationDependencies.getDatabaseObserver().unregisterObserver(observer);
  }

  public static class Factory extends ViewModelProvider.NewInstanceFactory {

    private final boolean isArchived;

    public Factory(boolean isArchived) {
      this.isArchived = isArchived;
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection ConstantConditions
      return modelClass.cast(new ConversationListViewModel(ApplicationDependencies.getApplication(), new SearchRepository(), isArchived));
    }
  }
}