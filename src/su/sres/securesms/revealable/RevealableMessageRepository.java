package su.sres.securesms.revealable;

import android.content.Context;

import androidx.annotation.NonNull;

import su.sres.securesms.database.AttachmentDatabase;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.MmsDatabase;
import su.sres.securesms.database.model.MmsMessageRecord;
import su.sres.securesms.logging.Log;
import su.sres.securesms.util.concurrent.SignalExecutors;
import org.whispersystems.libsignal.util.guava.Optional;

class RevealableMessageRepository {

    private static final String TAG = Log.tag(RevealableMessageRepository.class);

    private final MmsDatabase mmsDatabase;

    RevealableMessageRepository(@NonNull Context context) {
        this.mmsDatabase = DatabaseFactory.getMmsDatabase(context);
    }

    void getMessage(long messageId, @NonNull Callback<Optional<MmsMessageRecord>> callback) {
        SignalExecutors.BOUNDED.execute(() -> {
            try (MmsDatabase.Reader reader = mmsDatabase.readerFor(mmsDatabase.getMessage(messageId))) {
                MmsMessageRecord record = (MmsMessageRecord) reader.getNext();
                callback.onComplete(Optional.fromNullable(record));
            }
        });
    }

    interface Callback<T> {
        void onComplete(T result);
    }
}