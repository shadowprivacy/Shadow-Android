package su.sres.securesms.conversation;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.mediasend.Media;
import su.sres.securesms.mediasend.MediaRepository;

import java.util.List;

class ConversationViewModel extends ViewModel {

    private final Context                      context;
    private final MediaRepository              mediaRepository;
    private final MutableLiveData<List<Media>> recentMedia;

    private ConversationViewModel() {
        this.context         = ApplicationDependencies.getApplication();
        this.mediaRepository = new MediaRepository();
        this.recentMedia     = new MutableLiveData<>();
    }

    void onAttachmentKeyboardOpen() {
        mediaRepository.getMediaInBucket(context, Media.ALL_MEDIA_BUCKET_ID, recentMedia::postValue);
    }

    @NonNull LiveData<List<Media>> getRecentMedia() {
        return recentMedia;
    }

    static class Factory extends ViewModelProvider.NewInstanceFactory {
        @Override
        public @NonNull<T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            //noinspection ConstantConditions
            return modelClass.cast(new ConversationViewModel());
        }
    }
}