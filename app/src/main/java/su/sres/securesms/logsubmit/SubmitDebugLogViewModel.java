package su.sres.securesms.logsubmit;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import su.sres.core.util.ThreadUtil;
import su.sres.core.util.logging.Log;
import su.sres.core.util.tracing.Tracer;
import su.sres.paging.PagedData;
import su.sres.paging.PagingConfig;
import su.sres.paging.PagingController;
import su.sres.paging.ProxyPagingController;
import su.sres.securesms.database.LogDatabase;
import su.sres.securesms.dependencies.ApplicationDependencies;

import org.whispersystems.libsignal.util.guava.Optional;

import java.util.ArrayList;
import java.util.List;

public class SubmitDebugLogViewModel extends ViewModel {

  private final SubmitDebugLogRepository        repo;
  private final MutableLiveData<Mode>           mode;
  private final ProxyPagingController<Long>     pagingController;
  private final List<LogLine>                   staticLines;
  private final MediatorLiveData<List<LogLine>> lines;
  private final long                            firstViewTime;
  private final byte[]                          trace;

  private SubmitDebugLogViewModel() {
    this.repo             = new SubmitDebugLogRepository();
    this.mode             = new MutableLiveData<>();
    this.trace            = Tracer.getInstance().serialize();
    this.pagingController = new ProxyPagingController<>();
    this.firstViewTime    = System.currentTimeMillis();
    this.staticLines      = new ArrayList<>();
    this.lines            = new MediatorLiveData<>();

    repo.getPrefixLogLines(staticLines -> {
      this.staticLines.addAll(staticLines);

      Log.blockUntilAllWritesFinished();
      LogDatabase.getInstance(ApplicationDependencies.getApplication()).trimToSize();

      LogDataSource dataSource = new LogDataSource(ApplicationDependencies.getApplication(), staticLines, firstViewTime);
      PagingConfig config = new PagingConfig.Builder().setPageSize(100)
                                                      .setBufferPages(3)
                                                      .setStartIndex(0)
                                                      .build();

      PagedData<Long, LogLine> pagedData = PagedData.create(dataSource, config);

      ThreadUtil.runOnMain(() -> {
        pagingController.set(pagedData.getController());
        lines.addSource(pagedData.getData(), lines::setValue);
        mode.setValue(Mode.NORMAL);
      });
    });
  }

  @NonNull LiveData<List<LogLine>> getLines() {
    return lines;
  }

  boolean hasLines() {
    return lines.getValue().size() > 0;
  }

  @NonNull PagingController getPagingController() {
    return pagingController;
  }

  @NonNull LiveData<Mode> getMode() {
    return mode;
  }

  @NonNull LiveData<Optional<String>> onSubmitClicked() {
    mode.postValue(Mode.SUBMITTING);

    MutableLiveData<Optional<String>> result = new MutableLiveData<>();

    repo.submitLogWithPrefixLines(firstViewTime, staticLines, trace, value -> {
      mode.postValue(Mode.NORMAL);
      result.postValue(value);
    });

    return result;
  }

  void onQueryUpdated(@NonNull String query) {
    throw new UnsupportedOperationException("Not yet implemented.");
  }

  void onSearchClosed() {
    throw new UnsupportedOperationException("Not yet implemented.");
  }

  void onEditButtonPressed() {
    throw new UnsupportedOperationException("Not yet implemented.");
  }

  void onDoneEditingButtonPressed() {
    throw new UnsupportedOperationException("Not yet implemented.");
  }

  void onLogDeleted(@NonNull LogLine line) {
    throw new UnsupportedOperationException("Not yet implemented.");
  }

  boolean onBackPressed() {
    if (mode.getValue() == Mode.EDIT) {
      mode.setValue(Mode.NORMAL);
      return true;
    } else {
      return false;
    }
  }

  enum Mode {
    NORMAL, EDIT, SUBMITTING
  }

  public static class Factory extends ViewModelProvider.NewInstanceFactory {
    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection ConstantConditions
      return modelClass.cast(new SubmitDebugLogViewModel());
    }
  }
}