package su.sres.securesms.profiles.manage;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;

import su.sres.core.util.concurrent.SignalExecutors;
import su.sres.core.util.logging.Log;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.profiles.AvatarHelper;
import su.sres.securesms.profiles.ProfileName;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.util.ProfileUtil;
import su.sres.signalservice.api.util.StreamDetails;

import java.io.ByteArrayInputStream;
import java.io.IOException;

final class ManageProfileRepository {

    private static final String TAG = Log.tag(ManageProfileRepository.class);

    public void setName(@NonNull Context context, @NonNull ProfileName profileName, @NonNull Consumer<Result> callback) {
        SignalExecutors.UNBOUNDED.execute(() -> {
            try {
                ProfileUtil.uploadProfileWithName(context, profileName);
                DatabaseFactory.getRecipientDatabase(context).setProfileName(Recipient.self().getId(), profileName);
                callback.accept(Result.SUCCESS);
            } catch (IOException e) {
                Log.w(TAG, "Failed to upload profile during name change.", e);
                callback.accept(Result.FAILURE_NETWORK);
            }
        });
    }

    public void setAbout(@NonNull Context context, @NonNull String about, @NonNull String emoji, @NonNull Consumer<Result> callback) {
        SignalExecutors.UNBOUNDED.execute(() -> {
            try {
                ProfileUtil.uploadProfileWithAbout(context, about, emoji);
                DatabaseFactory.getRecipientDatabase(context).setAbout(Recipient.self().getId(), about, emoji);
                callback.accept(Result.SUCCESS);
            } catch (IOException e) {
                Log.w(TAG, "Failed to upload profile during about change.", e);
                callback.accept(Result.FAILURE_NETWORK);
            }
        });
    }

    public void setAvatar(@NonNull Context context, @NonNull byte[] data, @NonNull String contentType, @NonNull Consumer<Result> callback) {
        SignalExecutors.UNBOUNDED.execute(() -> {
            try {
                ProfileUtil.uploadProfileWithAvatar(context, new StreamDetails(new ByteArrayInputStream(data), contentType, data.length));
                AvatarHelper.setAvatar(context, Recipient.self().getId(), new ByteArrayInputStream(data));
                callback.accept(Result.SUCCESS);
            } catch (IOException e) {
                Log.w(TAG, "Failed to upload profile during avatar change.", e);
                callback.accept(Result.FAILURE_NETWORK);
            }
        });
    }

    public void clearAvatar(@NonNull Context context, @NonNull Consumer<Result> callback) {
        SignalExecutors.UNBOUNDED.execute(() -> {
            try {
                ProfileUtil.uploadProfileWithAvatar(context, null);
                AvatarHelper.delete(context, Recipient.self().getId());
                callback.accept(Result.SUCCESS);
            } catch (IOException e) {
                Log.w(TAG, "Failed to upload profile during name change.", e);
                callback.accept(Result.FAILURE_NETWORK);
            }
        });
    }

    enum Result {
        SUCCESS, FAILURE_NETWORK
    }
}
