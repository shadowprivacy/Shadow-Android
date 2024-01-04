package su.sres.securesms.conversation;

import android.app.Application;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import android.database.ContentObserver;
import android.os.Handler;
import androidx.annotation.NonNull;

import android.os.Looper;
import android.text.TextUtils;

import su.sres.securesms.components.emoji.EmojiUtil;
import su.sres.securesms.database.CursorList;
import su.sres.securesms.database.DatabaseContentProviders;
import su.sres.securesms.database.model.StickerRecord;
import su.sres.securesms.emoji.EmojiSource;
import su.sres.securesms.stickers.StickerSearchRepository;
import su.sres.securesms.util.CloseableLiveData;
import su.sres.securesms.util.Throttler;

import java.util.List;

class ConversationStickerViewModel extends ViewModel {

    private final Application                          application;
    private final StickerSearchRepository              repository;
    private final MutableLiveData<List<StickerRecord>> stickers;
    private final MutableLiveData<Boolean>             stickersAvailable;
    private final Throttler                            availabilityThrottler;
    private final ContentObserver                      packObserver;

    private ConversationStickerViewModel(@NonNull Application application, @NonNull StickerSearchRepository repository) {
        this.application           = application;
        this.repository            = repository;
        this.stickers              = new MutableLiveData<>();
        this.stickersAvailable     = new MutableLiveData<>();
        this.availabilityThrottler = new Throttler(500);
        this.packObserver          = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                availabilityThrottler.publish(() -> repository.getStickerFeatureAvailability(stickersAvailable::postValue));
            }
        };

        application.getContentResolver().registerContentObserver(DatabaseContentProviders.StickerPack.CONTENT_URI, true, packObserver);
    }

    @NonNull LiveData<List<StickerRecord>> getStickerResults() {
        return stickers;
    }

    @NonNull LiveData<Boolean> getStickersAvailability() {
        repository.getStickerFeatureAvailability(stickersAvailable::postValue);
        return stickersAvailable;
    }

    void onInputTextUpdated(@NonNull String text) {
        if (TextUtils.isEmpty(text) || text.length() > EmojiSource.getLatest().getMaxEmojiLength()) {
            stickers.setValue(CursorList.emptyList());
        } else {
            repository.searchByEmoji(text, stickers::postValue);
        }
    }

    @Override
    protected void onCleared() {
        application.getContentResolver().unregisterContentObserver(packObserver);
    }

    static class Factory extends ViewModelProvider.NewInstanceFactory {
        private final Application             application;
        private final StickerSearchRepository repository;

        public Factory(@NonNull Application application, @NonNull StickerSearchRepository repository) {
            this.application = application;
            this.repository  = repository;
        }

        @Override
        public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            //noinspection ConstantConditions
            return modelClass.cast(new ConversationStickerViewModel(application, repository));
        }
    }
}