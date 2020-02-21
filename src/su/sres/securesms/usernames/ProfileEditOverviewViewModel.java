package su.sres.securesms.usernames;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import su.sres.securesms.CreateProfileActivity;
import su.sres.securesms.R;
import su.sres.securesms.avatar.AvatarSelection;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.logging.Log;
import su.sres.securesms.profiles.AvatarHelper;
import su.sres.securesms.profiles.ProfileMediaConstraints;
import su.sres.securesms.util.BitmapDecodingException;
import su.sres.securesms.util.BitmapUtil;
import su.sres.securesms.util.SingleLiveEvent;
import su.sres.securesms.util.TextSecurePreferences;
import su.sres.securesms.util.Util;
import su.sres.securesms.util.concurrent.SignalExecutors;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

class ProfileEditOverviewViewModel extends ViewModel {

    private static final String TAG = Log.tag(ProfileEditOverviewViewModel.class);

    private final Application                       application;
    private final ProfileEditOverviewRepository     repo;
    private final SingleLiveEvent<Event>            event;
    private final MutableLiveData<Optional<byte[]>> avatar;
    private final MutableLiveData<Boolean>          loading;
    private final MutableLiveData<Optional<String>> profileName;
    private final MutableLiveData<Optional<String>> username;

    private File captureFile;

    private ProfileEditOverviewViewModel() {
        this.application = ApplicationDependencies.getApplication();
        this.repo        = new ProfileEditOverviewRepository();
        this.avatar      = new MutableLiveData<>();
        this.loading     = new MutableLiveData<>();
        this.profileName = new MutableLiveData<>();
        this.username    = new MutableLiveData<>();
        this.event       = new SingleLiveEvent<>();

        profileName.setValue(Optional.fromNullable(TextSecurePreferences.getProfileName(application)));
        username.setValue(Optional.fromNullable(TextSecurePreferences.getLocalUsername(application)));
        loading.setValue(false);

        repo.getProfileAvatar(avatar::postValue);
        repo.getProfileName(profileName::postValue);
        repo.getUsername(username::postValue);
    }

    void onAvatarClicked(@NonNull Fragment fragment) {
        //noinspection ConstantConditions Initial value is set
        captureFile = AvatarSelection.startAvatarSelection(fragment, avatar.getValue().isPresent(), true);
    }

    boolean onActivityResult(@NonNull Fragment fragment, int requestCode, int resultCode, @Nullable Intent data) {
        switch (requestCode) {
            case AvatarSelection.REQUEST_CODE_AVATAR:
                handleAvatarResult(fragment, resultCode, data);
                return true;
            case AvatarSelection.REQUEST_CODE_CROP_IMAGE:
                handleCropImage(resultCode, data);
                return true;
            default:
                return false;
        }
    }

    void onResume() {
        profileName.setValue(Optional.fromNullable(TextSecurePreferences.getProfileName(application)));
        username.setValue(Optional.fromNullable(TextSecurePreferences.getLocalUsername(application)));
    }

    @NonNull LiveData<Optional<byte[]>> getAvatar() {
        return avatar;
    }

    @NonNull LiveData<Boolean> getLoading() {
        return loading;
    }

    @NonNull LiveData<Optional<String>> getProfileName() {
        return profileName;
    }

    @NonNull LiveData<Optional<String>> getUsername() {
        return username;
    }

    @NonNull LiveData<Event> getEvents() {
        return event;
    }

    private void handleAvatarResult(@NonNull Fragment fragment, int resultCode, @Nullable Intent data) {
        if (resultCode != Activity.RESULT_OK) {
            Log.w(TAG, "Bad result for REQUEST_CODE_AVATAR.");
            event.postValue(Event.IMAGE_SAVE_FAILURE);
            return;
        }

        if (data != null && data.getBooleanExtra("delete", false)) {
            Log.i(TAG, "Deleting profile avatar.");

            Optional<byte[]> oldAvatar = avatar.getValue();

            avatar.setValue(Optional.absent());
            loading.setValue(true);

            repo.deleteProfileAvatar(result -> {
                switch (result) {
                    case SUCCESS:
                        loading.postValue(false);
                        break;
                    case NETWORK_FAILURE:
                        loading.postValue(false);
                        avatar.postValue(oldAvatar);
                        event.postValue(Event.NETWORK_ERROR);
                        break;
                }
            });
        } else {
            Uri outputFile = Uri.fromFile(new File(application.getCacheDir(), "cropped"));
            Uri inputFile  = (data != null ? data.getData() : null);

            if (inputFile == null && captureFile != null) {
                inputFile = Uri.fromFile(captureFile);
            }

            if (inputFile != null) {
                AvatarSelection.circularCropImage(fragment, inputFile, outputFile, R.string.CropImageActivity_profile_avatar);
            } else {
                Log.w(TAG, "No input file!");
                event.postValue(Event.IMAGE_SAVE_FAILURE);
            }
        }
    }

    private void handleCropImage(int resultCode, @Nullable Intent data) {
        if (resultCode != Activity.RESULT_OK) {
            Log.w(TAG, "Bad result for REQUEST_CODE_CROP_IMAGE.");
            event.postValue(Event.IMAGE_SAVE_FAILURE);
            return;
        }

        Optional<byte[]> oldAvatar = avatar.getValue();

        loading.setValue(true);

        SignalExecutors.BOUNDED.execute(() -> {
            try {
                BitmapUtil.ScaleResult scaled = BitmapUtil.createScaledBytes(application, AvatarSelection.getResultUri(data), new ProfileMediaConstraints());

                if (captureFile != null) {
                    captureFile.delete();
                }

                avatar.postValue(Optional.of(scaled.getBitmap()));

                repo.setProfileAvatar(scaled.getBitmap(), result -> {
                    switch (result) {
                        case SUCCESS:
                            loading.postValue(false);
                            break;
                        case NETWORK_FAILURE:
                            loading.postValue(false);
                            avatar.postValue(oldAvatar);
                            event.postValue(Event.NETWORK_ERROR);
                            break;
                    }
                });
            } catch (BitmapDecodingException e) {
                event.postValue(Event.IMAGE_SAVE_FAILURE);
            }
        });
    }

    @Override
    protected void onCleared() {
        if (captureFile != null) {
            captureFile.delete();
        }
    }

    enum Event {
        IMAGE_SAVE_FAILURE, NETWORK_ERROR
    }

    static class Factory extends ViewModelProvider.NewInstanceFactory {
        @Override
        public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            //noinspection ConstantConditions
            return modelClass.cast(new ProfileEditOverviewViewModel());
        }
    }
}