package su.sres.securesms.messagedetails;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;

import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.MmsDatabase;
import su.sres.securesms.database.MmsSmsDatabase;
import su.sres.securesms.database.SmsDatabase;
import su.sres.securesms.database.model.MessageRecord;
import su.sres.securesms.util.concurrent.SignalExecutors;

final class MessageRecordLiveData extends LiveData<MessageRecord> {

    private final Context         context;
    private final String          type;
    private final Long            messageId;
    private final ContentObserver obs;

    private @Nullable Cursor cursor;

    MessageRecordLiveData(Context context, String type, Long messageId) {
        this.context   = context;
        this.type      = type;
        this.messageId = messageId;

        obs = new ContentObserver(null) {
            @Override
            public void onChange(boolean selfChange) {
                SignalExecutors.BOUNDED.execute(() -> resetCursor());
            }
        };
    }

    @Override
    protected void onActive() {
        retrieveMessageRecord();
    }

    @Override
    protected void onInactive() {
        SignalExecutors.BOUNDED.execute(this::destroyCursor);
    }

    private void retrieveMessageRecord() {
        SignalExecutors.BOUNDED.execute(this::retrieveMessageRecordActual);
    }

    @WorkerThread
    private synchronized void destroyCursor() {
        if (cursor != null) {
            cursor.unregisterContentObserver(obs);
            cursor.close();
            cursor = null;
        }
    }

    @WorkerThread
    private synchronized void resetCursor() {
        destroyCursor();
        retrieveMessageRecord();
    }

    @WorkerThread
    private synchronized void retrieveMessageRecordActual() {
        if (cursor != null) {
            return;
        }
        switch (type) {
            case MmsSmsDatabase.SMS_TRANSPORT:
                handleSms();
                break;
            case MmsSmsDatabase.MMS_TRANSPORT:
                handleMms();
                break;
            default:
                throw new AssertionError("no valid message type specified");
        }
    }

    @WorkerThread
    private synchronized void handleSms() {
        final SmsDatabase   db     = DatabaseFactory.getSmsDatabase(context);
        final Cursor        cursor = db.getVerboseMessageCursor(messageId);
        final MessageRecord record = db.readerFor(cursor).getNext();

        postValue(record);
        cursor.registerContentObserver(obs);
        this.cursor = cursor;
    }

    @WorkerThread
    private synchronized void handleMms() {
        final MmsDatabase   db     = DatabaseFactory.getMmsDatabase(context);
        final Cursor        cursor = db.getVerboseMessage(messageId);
        final MessageRecord record = db.readerFor(cursor).getNext();

        postValue(record);
        cursor.registerContentObserver(obs);
        this.cursor = cursor;
    }
}