package su.sres.securesms.giph.mp4;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.annimon.stream.Stream;

import su.sres.paging.PagedData;
import su.sres.paging.PagingConfig;
import su.sres.paging.PagingController;
import su.sres.securesms.giph.model.GiphyImage;
import su.sres.securesms.util.DefaultValueLiveData;
import su.sres.securesms.util.SingleLiveEvent;

import java.util.List;
import java.util.Objects;

/**
 * ViewModel which drives GiphyMp4Fragment. This is to be bound to the activity,
 * and used as both a data provider and controller.
 */
public final class GiphyMp4ViewModel extends ViewModel {

  private final GiphyMp4Repository                             repository;
  private final MutableLiveData<PagedData<String, GiphyImage>> pagedData;
  private final LiveData<List<GiphyImage>>                     images;
  private final LiveData<PagingController<String>>             pagingController;
  private final SingleLiveEvent<GiphyMp4SaveResult>            saveResultEvents;
  private final boolean                                        isForMms;

  private String query;

  private GiphyMp4ViewModel(boolean isForMms) {
    this.isForMms         = isForMms;
    this.repository       = new GiphyMp4Repository();
    this.pagedData        = new DefaultValueLiveData<>(getGiphyImagePagedData(null));
    this.saveResultEvents = new SingleLiveEvent<>();
    this.pagingController = Transformations.map(pagedData, PagedData::getController);
    this.images           = Transformations.switchMap(pagedData, pagedData -> Transformations.map(pagedData.getData(),
                                                                                                  data -> Stream.of(data)
                                                                                                                .filter(g -> g != null)
                                                                                                                .filterNot(g -> TextUtils.isEmpty(isForMms ? g.getGifMmsUrl() : g.getGifUrl()))
                                                                                                                .filterNot(g -> TextUtils.isEmpty(g.getMp4PreviewUrl()))
                                                                                                                .filterNot(g -> TextUtils.isEmpty(g.getStillUrl()))
                                                                                                                .toList()));
  }

  LiveData<PagedData<String, GiphyImage>> getPagedData() {
    return pagedData;
  }

  public void updateSearchQuery(@Nullable String query) {
    if (!Objects.equals(query, this.query)) {
      this.query = query;

      pagedData.setValue(getGiphyImagePagedData(query));
    }
  }

  public void saveToBlob(@NonNull GiphyImage giphyImage) {
    saveResultEvents.postValue(new GiphyMp4SaveResult.InProgress());
    repository.saveToBlob(giphyImage, isForMms, saveResultEvents::postValue);
  }

  public @NonNull LiveData<GiphyMp4SaveResult> getSaveResultEvents() {
    return saveResultEvents;
  }

  public @NonNull LiveData<List<GiphyImage>> getImages() {
    return images;
  }

  public @NonNull LiveData<PagingController<String>> getPagingController() {
    return pagingController;
  }

  private PagedData<String, GiphyImage> getGiphyImagePagedData(@Nullable String query) {
    return PagedData.create(new GiphyMp4PagedDataSource(query),
                            new PagingConfig.Builder().setPageSize(20)
                                                      .setBufferPages(1)
                                                      .build());
  }

  public static class Factory implements ViewModelProvider.Factory {
    private final boolean isForMms;

    public Factory(boolean isForMms) {
      this.isForMms = isForMms;
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      return Objects.requireNonNull(modelClass.cast(new GiphyMp4ViewModel(isForMms)));
    }
  }
}
