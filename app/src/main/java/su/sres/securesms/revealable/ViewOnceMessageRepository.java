package su.sres.securesms.revealable;

import android.content.Context;

import androidx.annotation.NonNull;

import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.MessageDatabase;
import su.sres.securesms.database.MmsDatabase;
import su.sres.securesms.database.model.MmsMessageRecord;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobs.MultiDeviceViewedUpdateJob;
import su.sres.securesms.jobs.SendViewedReceiptJob;
import su.sres.core.util.logging.Log;
import su.sres.core.util.concurrent.SignalExecutors;
import su.sres.securesms.util.FeatureFlags;

import org.whispersystems.libsignal.util.guava.Optional;

import java.util.Collections;

class ViewOnceMessageRepository {

    private static final String TAG = Log.tag(ViewOnceMessageRepository.class);

    private final MessageDatabase mmsDatabase;

    ViewOnceMessageRepository(@NonNull Context context) {
        this.mmsDatabase = DatabaseFactory.getMmsDatabase(context);
    }

    void getMessage(long messageId, @NonNull Callback<Optional<MmsMessageRecord>> callback) {
        SignalExecutors.BOUNDED.execute(() -> {
            try (MmsDatabase.Reader reader = MmsDatabase.readerFor(mmsDatabase.getMessageCursor(messageId))) {
                MmsMessageRecord record = (MmsMessageRecord) reader.getNext();
                if (FeatureFlags.sendViewedReceipts()) {
                    MessageDatabase.MarkedMessageInfo info = mmsDatabase.setIncomingMessageViewed(record.getId());
                    if (info != null) {
                        ApplicationDependencies.getJobManager().add(new SendViewedReceiptJob(record.getThreadId(),
                                info.getSyncMessageId().getRecipientId(),
                                info.getSyncMessageId().getTimetamp()));
                        MultiDeviceViewedUpdateJob.enqueue(Collections.singletonList(info.getSyncMessageId()));
                    }
                }
                callback.onComplete(Optional.fromNullable(record));
            }
        });
    }

    interface Callback<T> {
        void onComplete(T result);
    }
}