package su.sres.securesms.profiles.manage;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;

import su.sres.core.util.concurrent.SignalExecutors;
import su.sres.core.util.logging.Log;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.profiles.ProfileName;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.util.ProfileUtil;

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
                Log.w(TAG, "Failed to upload profile during name change.", e);
                callback.accept(Result.FAILURE_NETWORK);
            }
        });
    }

    enum Result {
        SUCCESS, FAILURE_NETWORK
    }
}
