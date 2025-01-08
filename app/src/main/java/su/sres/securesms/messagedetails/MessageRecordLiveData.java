package su.sres.securesms.messagedetails;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;

import su.sres.securesms.database.DatabaseObserver;
import su.sres.securesms.database.MessageDatabase;
import su.sres.securesms.database.MmsDatabase;
import su.sres.securesms.database.MmsSmsDatabase;
import su.sres.securesms.database.ShadowDatabase;
import su.sres.securesms.database.SmsDatabase;
import su.sres.securesms.database.model.MessageRecord;
import su.sres.core.util.concurrent.SignalExecutors;
import su.sres.securesms.dependencies.ApplicationDependencies;

final class MessageRecordLiveData extends LiveData<MessageRecord> {

  private final Context                   context;
  private final String                    type;
  private final Long                      messageId;
  private final DatabaseObserver.Observer observer;

  private @Nullable Cursor cursor;

  MessageRecordLiveData(Context context, String type, Long messageId) {
    this.context   = context;
    this.type      = type;
    this.messageId = messageId;

    this.observer = () -> SignalExecutors.BOUNDED.execute(this::resetCursor);
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
    ApplicationDependencies.getDatabaseObserver().unregisterObserver(observer);

    if (cursor != null) {
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
    final MessageDatabase db     = ShadowDatabase.sms();
    final Cursor          cursor = db.getMessageCursor(messageId);
    final MessageRecord   record = SmsDatabase.readerFor(cursor).getNext();

    postValue(record);
    ApplicationDependencies.getDatabaseObserver().registerVerboseConversationObserver(record.getThreadId(), observer);
    this.cursor = cursor;
  }

  @WorkerThread
  private synchronized void handleMms() {
    final MessageDatabase db     = ShadowDatabase.mms();
    final Cursor          cursor = db.getMessageCursor(messageId);
    final MessageRecord   record = MmsDatabase.readerFor(cursor).getNext();

    postValue(record);
    ApplicationDependencies.getDatabaseObserver().registerVerboseConversationObserver(record.getThreadId(), observer);
    this.cursor = cursor;
  }
}