package su.sres.pagingtest;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import su.sres.paging.PagedDataSource;
import su.sres.paging.PagingController;
import su.sres.paging.PagingConfig;
import su.sres.paging.PagedData;

import java.util.ArrayList;
import java.util.List;

public class MainViewModel extends ViewModel {

  private final PagedData<String, Item> pagedData;
  private final MainDataSource          dataSource;

  public MainViewModel() {
    this.dataSource = new MainDataSource(1000);
    this.pagedData  = PagedData.create(dataSource, new PagingConfig.Builder().setBufferPages(3)
                                                                             .setPageSize(25)
                                                                             .build());
  }

  public void onItemClicked(@NonNull String key) {
    dataSource.updateItem(key);
    pagedData.getController().onDataItemChanged(key);
  }

  public @NonNull LiveData<List<Item>> getList() {
    return pagedData.getData();
  }

  public @NonNull PagingController<String> getPagingController() {
    return pagedData.getController();
  }

  public void prependItems() {
    String key = dataSource.prepend();
    pagedData.getController().onDataItemInserted(key, 0);
  }
}