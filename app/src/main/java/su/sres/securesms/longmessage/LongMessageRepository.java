package su.sres.securesms.longmessage;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import su.sres.core.util.StreamUtil;
import su.sres.securesms.conversation.ConversationMessage.ConversationMessageFactory;
import su.sres.securesms.database.MessageDatabase;
import su.sres.securesms.database.MmsDatabase;
import su.sres.securesms.database.ShadowDatabase;
import su.sres.securesms.database.SmsDatabase;
import su.sres.securesms.database.model.MessageRecord;
import su.sres.securesms.database.model.MmsMessageRecord;
import su.sres.core.util.logging.Log;
import su.sres.securesms.mms.PartAuthority;
import su.sres.securesms.mms.TextSlide;
import su.sres.core.util.concurrent.SignalExecutors;

import org.whispersystems.libsignal.util.guava.Optional;

import java.io.IOException;
import java.io.InputStream;

class LongMessageRepository {

  private final static String TAG = Log.tag(LongMessageRepository.class);

  private final MessageDatabase mmsDatabase;
  private final MessageDatabase smsDatabase;

  LongMessageRepository(@NonNull Context context) {
    this.mmsDatabase = ShadowDatabase.mms();
    this.smsDatabase = ShadowDatabase.sms();
  }

  void getMessage(@NonNull Context context, long messageId, boolean isMms, @NonNull Callback<Optional<LongMessage>> callback) {
    SignalExecutors.BOUNDED.execute(() -> {
      if (isMms) {
        callback.onComplete(getMmsLongMessage(context, mmsDatabase, messageId));
      } else {
        callback.onComplete(getSmsLongMessage(context, smsDatabase, messageId));
      }
    });
  }

  @WorkerThread
  private Optional<LongMessage> getMmsLongMessage(@NonNull Context context, @NonNull MessageDatabase mmsDatabase, long messageId) {
    Optional<MmsMessageRecord> record = getMmsMessage(mmsDatabase, messageId);

    if (record.isPresent()) {
      TextSlide textSlide = record.get().getSlideDeck().getTextSlide();

      if (textSlide != null && textSlide.getUri() != null) {
        return Optional.of(new LongMessage(ConversationMessageFactory.createWithUnresolvedData(context, record.get(), readFullBody(context, textSlide.getUri()))));
      } else {
        return Optional.of(new LongMessage(ConversationMessageFactory.createWithUnresolvedData(context, record.get())));
      }
    } else {
      return Optional.absent();
    }
  }

  @WorkerThread
  private Optional<LongMessage> getSmsLongMessage(@NonNull Context context, @NonNull MessageDatabase smsDatabase, long messageId) {
    Optional<MessageRecord> record = getSmsMessage(smsDatabase, messageId);

    if (record.isPresent()) {
      return Optional.of(new LongMessage(ConversationMessageFactory.createWithUnresolvedData(context, record.get())));
    } else {
      return Optional.absent();
    }
  }


  @WorkerThread
  private Optional<MmsMessageRecord> getMmsMessage(@NonNull MessageDatabase mmsDatabase, long messageId) {
    try (Cursor cursor = mmsDatabase.getMessageCursor(messageId)) {
      return Optional.fromNullable((MmsMessageRecord) MmsDatabase.readerFor(cursor).getNext());
    }
  }

  @WorkerThread
  private Optional<MessageRecord> getSmsMessage(@NonNull MessageDatabase smsDatabase, long messageId) {
    try (Cursor cursor = smsDatabase.getMessageCursor(messageId)) {
      return Optional.fromNullable(SmsDatabase.readerFor(cursor).getNext());
    }
  }

  private @NonNull String readFullBody(@NonNull Context context, @NonNull Uri uri) {
    try (InputStream stream = PartAuthority.getAttachmentStream(context, uri)) {
      return StreamUtil.readFullyAsString(stream);
    } catch (IOException e) {
      Log.w(TAG, "Failed to read full text body.", e);
      return "";
    }
  }

  interface Callback<T> {
    void onComplete(T result);
  }
}