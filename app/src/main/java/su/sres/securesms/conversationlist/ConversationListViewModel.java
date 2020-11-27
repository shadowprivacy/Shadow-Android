package su.sres.securesms.conversationlist;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import android.app.Application;
import android.database.ContentObserver;
import android.os.Handler;
import androidx.annotation.NonNull;
import androidx.paging.DataSource;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;

import android.text.TextUtils;

import su.sres.securesms.conversationlist.model.Conversation;
import su.sres.securesms.conversationlist.model.SearchResult;
import su.sres.securesms.database.DatabaseContentProviders;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.logging.Log;
import su.sres.securesms.megaphone.Megaphone;
import su.sres.securesms.megaphone.MegaphoneRepository;
import su.sres.securesms.megaphone.Megaphones;
import su.sres.securesms.search.SearchRepository;
import su.sres.securesms.util.Debouncer;
import su.sres.securesms.util.Util;
import su.sres.securesms.util.concurrent.SignalExecutors;
import su.sres.securesms.util.livedata.LiveDataUtil;
import su.sres.securesms.util.paging.Invalidator;

class ConversationListViewModel extends ViewModel {

    private static final String TAG = Log.tag(ConversationListViewModel.class);

    private final Application                   application;
    private final MutableLiveData<Megaphone>    megaphone;
    private final MutableLiveData<SearchResult> searchResult;
    private final LiveData<ConversationList>    conversationList;
    private final SearchRepository              searchRepository;
    private final MegaphoneRepository           megaphoneRepository;
    private final Debouncer                     debouncer;
    private final ContentObserver               observer;
    private final Invalidator                   invalidator;

    private String lastQuery;

    private ConversationListViewModel(@NonNull Application application, @NonNull SearchRepository searchRepository, boolean isArchived) {
        this.application         = application;
        this.megaphone           = new MutableLiveData<>();
        this.searchResult        = new MutableLiveData<>();
        this.searchRepository    = searchRepository;
        this.megaphoneRepository = ApplicationDependencies.getMegaphoneRepository();
        this.debouncer           = new Debouncer(300);
        this.invalidator         = new Invalidator();
        this.observer            = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                if (!TextUtils.isEmpty(getLastQuery())) {
                    searchRepository.query(getLastQuery(), searchResult::postValue);
                }
            }
        };

        DataSource.Factory<Integer, Conversation> factory = new ConversationListDataSource.Factory(application, invalidator, isArchived);
        PagedList.Config                          config  = new PagedList.Config.Builder()
                .setPageSize(15)
                .setInitialLoadSizeHint(30)
                .setEnablePlaceholders(true)
                .build();

        LiveData<PagedList<Conversation>> conversationList = new LivePagedListBuilder<>(factory, config).setFetchExecutor(ConversationListDataSource.EXECUTOR)
                .setInitialLoadKey(0)
                .build();

        application.getContentResolver().registerContentObserver(DatabaseContentProviders.ConversationList.CONTENT_URI, true, observer);

        this.conversationList = Transformations.switchMap(conversationList, conversation -> {
            if (conversation.getDataSource().isInvalid()) {
                Log.w(TAG, "Received an invalid conversation list. Ignoring.");
                return new MutableLiveData<>();
            }

            MutableLiveData<ConversationList> updated = new MutableLiveData<>();

            if (isArchived) {
                updated.postValue(new ConversationList(conversation, 0));
            } else {
                SignalExecutors.BOUNDED.execute(() -> {
                    int archiveCount = DatabaseFactory.getThreadDatabase(application).getArchivedConversationListCount();
                    updated.postValue(new ConversationList(conversation, archiveCount));
                });
            }

            return updated;
        });
    }

    @NonNull LiveData<SearchResult> getSearchResult() {
        return searchResult;
    }

    @NonNull LiveData<Megaphone> getMegaphone() {
        return megaphone;
    }

    @NonNull LiveData<ConversationList> getConversationList() {
        return conversationList;
    }

    void onVisible() {
        megaphoneRepository.getNextMegaphone(megaphone::postValue);
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
        application.getContentResolver().unregisterContentObserver(observer);
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

    final static class ConversationList {
        private final PagedList<Conversation> conversations;
        private final int                     archivedCount;

        ConversationList(PagedList<Conversation> conversations, int archivedCount) {
            this.conversations = conversations;
            this.archivedCount = archivedCount;
        }

        PagedList<Conversation> getConversations() {
            return conversations;
        }

        int getArchivedCount() {
            return archivedCount;
        }

        boolean isEmpty() {
            return conversations.isEmpty() && archivedCount == 0;
        }
    }
}