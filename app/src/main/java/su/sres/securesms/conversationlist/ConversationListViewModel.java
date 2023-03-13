package su.sres.securesms.conversationlist;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import android.app.Application;
import androidx.annotation.NonNull;

import android.text.TextUtils;

import java.util.List;

import su.sres.paging.PagedData;
import su.sres.paging.PagingConfig;
import su.sres.paging.PagingController;
import su.sres.securesms.conversationlist.model.Conversation;
import su.sres.securesms.conversationlist.model.SearchResult;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.DatabaseObserver;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.core.util.logging.Log;
import su.sres.securesms.megaphone.Megaphone;
import su.sres.securesms.megaphone.MegaphoneRepository;
import su.sres.securesms.megaphone.Megaphones;
import su.sres.securesms.search.SearchRepository;
import su.sres.securesms.util.Debouncer;
import su.sres.securesms.util.Util;
import su.sres.securesms.util.livedata.LiveDataUtil;
import su.sres.securesms.util.paging.Invalidator;

class ConversationListViewModel extends ViewModel {

    private static final String TAG = Log.tag(ConversationListViewModel.class);

    private static boolean coldStart = true;

    private final MutableLiveData<Megaphone>     megaphone;
    private final MutableLiveData<SearchResult>  searchResult;
    private final PagedData<Conversation> pagedData;
    private final LiveData<Boolean>              hasNoConversations;
    private final SearchRepository               searchRepository;
    private final MegaphoneRepository            megaphoneRepository;
    private final Debouncer                      debouncer;
    private final DatabaseObserver.Observer      observer;
    private final Invalidator                    invalidator;

    private String lastQuery;
    private int    pinnedCount;

    private ConversationListViewModel(@NonNull Application application, @NonNull SearchRepository searchRepository, boolean isArchived) {
        this.megaphone           = new MutableLiveData<>();
        this.searchResult        = new MutableLiveData<>();
        this.searchRepository    = searchRepository;
        this.megaphoneRepository = ApplicationDependencies.getMegaphoneRepository();
        this.debouncer           = new Debouncer(300);
        this.invalidator         = new Invalidator();
        this.pagedData           = PagedData.create(ConversationListDataSource.create(application, isArchived),
                new PagingConfig.Builder()
                        .setPageSize(15)
                        .setBufferPages(2)
                        .build());
        this.observer            = () -> {
            if (!TextUtils.isEmpty(getLastQuery())) {
                searchRepository.query(getLastQuery(), searchResult::postValue);
            }
            pagedData.getController().onDataInvalidated();
        };

        this.hasNoConversations = LiveDataUtil.mapAsync(pagedData.getData(), conversations -> {
            pinnedCount = DatabaseFactory.getThreadDatabase(application).getPinnedConversationListCount();

            if (conversations.size() > 0) {
                return false;
            } else {
                return DatabaseFactory.getThreadDatabase(application).getArchivedConversationListCount() == 0;
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

    void updateQuery(String query) {
        lastQuery = query;
        debouncer.publish(() -> searchRepository.query(query, result -> {
            Util.runOnMain(() -> {
                if (query.equals(lastQuery)) {
                    searchResult.setValue(result);
                }
            });
        }));
    }

    private @NonNull String getLastQuery() {
        return lastQuery == null ? "" : lastQuery;
    }

    @Override
    protected void onCleared() {
        invalidator.invalidate();
        debouncer.clear();
        ApplicationDependencies.getDatabaseObserver().unregisterObserver(observer);
    }

    public static class Factory extends ViewModelProvider.NewInstanceFactory {

        private final boolean isArchived;

        public Factory(boolean isArchived) {
            this.isArchived = isArchived;
        }

        @Override
        public @NonNull<T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            //noinspection ConstantConditions
            return modelClass.cast(new ConversationListViewModel(ApplicationDependencies.getApplication(), new SearchRepository(), isArchived));
        }
    }
}