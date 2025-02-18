package su.sres.securesms.conversation;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import su.sres.securesms.database.DatabaseObserver;
import su.sres.securesms.database.ShadowDatabase;
import su.sres.securesms.database.model.ThreadRecord;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.util.concurrent.SerialMonoLifoExecutor;
import su.sres.core.util.concurrent.SignalExecutors;

import org.whispersystems.libsignal.util.Pair;

import java.util.concurrent.Executor;

public class MessageCountsViewModel extends ViewModel {

  private static final Executor EXECUTOR = new SerialMonoLifoExecutor(SignalExecutors.BOUNDED);

  private final Application                      context;
  private final MutableLiveData<Long>            threadId = new MutableLiveData<>(-1L);
  private final LiveData<Pair<Integer, Integer>> unreadCounts;

  private DatabaseObserver.Observer observer;

  public MessageCountsViewModel() {
    this.context      = ApplicationDependencies.getApplication();
    this.unreadCounts = Transformations.switchMap(Transformations.distinctUntilChanged(threadId), id -> {

      MutableLiveData<Pair<Integer, Integer>> counts = new MutableLiveData<>(new Pair<>(0, 0));

      if (id == -1L) {
        return counts;
      }

      observer = new DatabaseObserver.Observer() {
        private int previousUnreadCount = -1;

        @Override
        public void onChanged() {
          EXECUTOR.execute(() -> {
            int unreadCount = getUnreadCount(context, id);
            if (unreadCount != previousUnreadCount) {
              previousUnreadCount = unreadCount;
              counts.postValue(new Pair<>(unreadCount, getUnreadMentionsCount(context, id)));
            }
          });
        }
      };

      observer.onChanged();

      ApplicationDependencies.getDatabaseObserver().registerConversationListObserver(observer);

      return counts;
    });

  }

  void setThreadId(long threadId) {
    this.threadId.setValue(threadId);
  }

  void clearThreadId() {
    this.threadId.postValue(-1L);
  }

  @NonNull LiveData<Integer> getUnreadMessagesCount() {
    return Transformations.map(unreadCounts, Pair::first);
  }

  @NonNull LiveData<Integer> getUnreadMentionsCount() {
    return Transformations.map(unreadCounts, Pair::second);
  }

  private int getUnreadCount(@NonNull Context context, long threadId) {
    ThreadRecord threadRecord = ShadowDatabase.threads().getThreadRecord(threadId);
    return threadRecord != null ? threadRecord.getUnreadCount() : 0;
  }

  private int getUnreadMentionsCount(@NonNull Context context, long threadId) {
    return ShadowDatabase.mms().getUnreadMentionCount(threadId);
  }

  @Override
  protected void onCleared() {
    if (observer != null) {
      ApplicationDependencies.getDatabaseObserver().unregisterObserver(observer);
    }
  }
}