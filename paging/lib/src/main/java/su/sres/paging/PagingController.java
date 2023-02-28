package su.sres.paging;

public interface PagingController {
    void onDataNeededAroundIndex(int aroundIndex);
    void onDataInvalidated();
}
