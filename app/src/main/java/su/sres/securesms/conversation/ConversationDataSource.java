package su.sres.securesms.conversation;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import su.sres.paging.PagedDataSource;
import su.sres.securesms.attachments.DatabaseAttachment;
import su.sres.securesms.conversation.ConversationData.MessageRequestData;
import su.sres.securesms.conversation.ConversationMessage.ConversationMessageFactory;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.MessageDatabase;
import su.sres.securesms.database.MmsSmsDatabase;
import su.sres.securesms.database.model.InMemoryMessageRecord;
import su.sres.securesms.database.model.MediaMmsMessageRecord;
import su.sres.securesms.database.model.Mention;
import su.sres.securesms.database.model.MessageId;
import su.sres.securesms.database.model.MessageRecord;
import su.sres.core.util.logging.Log;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.util.Stopwatch;
import su.sres.securesms.util.Util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Core data source for loading an individual conversation.
 */
class ConversationDataSource implements PagedDataSource<MessageId, ConversationMessage> {

  private static final String TAG = Log.tag(ConversationDataSource.class);

  private final Context            context;
  private final long               threadId;
  private final MessageRequestData messageRequestData;
  private final boolean            showUniversalExpireTimerUpdate;

  ConversationDataSource(@NonNull Context context, long threadId, @NonNull MessageRequestData messageRequestData, boolean showUniversalExpireTimerUpdate) {
    this.context                        = context;
    this.threadId                       = threadId;
    this.messageRequestData             = messageRequestData;
    this.showUniversalExpireTimerUpdate = showUniversalExpireTimerUpdate;
  }

  @Override
  public int size() {
    long startTime = System.currentTimeMillis();
    int size = DatabaseFactory.getMmsSmsDatabase(context).getConversationCount(threadId) +
               (messageRequestData.includeWarningUpdateMessage() ? 1 : 0) +
               (showUniversalExpireTimerUpdate ? 1 : 0);

    Log.d(TAG, "size() for thread " + threadId + ": " + (System.currentTimeMillis() - startTime) + " ms");

    return size;
  }

  @Override
  public @NonNull List<ConversationMessage> load(int start, int length, @NonNull CancellationSignal cancellationSignal) {
    Stopwatch           stopwatch        = new Stopwatch("load(" + start + ", " + length + "), thread " + threadId);
    MmsSmsDatabase      db               = DatabaseFactory.getMmsSmsDatabase(context);
    List<MessageRecord> records          = new ArrayList<>(length);
    MentionHelper       mentionHelper    = new MentionHelper();
    AttachmentHelper    attachmentHelper = new AttachmentHelper();

    try (MmsSmsDatabase.Reader reader = MmsSmsDatabase.readerFor(db.getConversation(threadId, start, length))) {
      MessageRecord record;
      while ((record = reader.getNext()) != null && !cancellationSignal.isCanceled()) {
        records.add(record);
        mentionHelper.add(record);
        attachmentHelper.add(record);
      }
    }

    if (messageRequestData.includeWarningUpdateMessage() && (start + length >= size())) {
      records.add(new InMemoryMessageRecord.NoGroupsInCommon(threadId, messageRequestData.isGroup()));
    }

    stopwatch.split("messages");

    mentionHelper.fetchMentions(context);

    if (showUniversalExpireTimerUpdate) {
      records.add(new InMemoryMessageRecord.UniversalExpireTimerUpdate(threadId));
    }

    stopwatch.split("mentions");

    attachmentHelper.fetchAttachments(context);

    stopwatch.split("attachments");

    records = attachmentHelper.buildUpdatedModels(context, records);

    stopwatch.split("attachment-models");

    List<ConversationMessage> messages = Stream.of(records)
                                               .map(m -> ConversationMessageFactory.createWithUnresolvedData(context, m, mentionHelper.getMentions(m.getId())))
                                               .toList();

    stopwatch.split("conversion");
    stopwatch.stop(TAG);

    return messages;
  }

  @Override
  public @Nullable ConversationMessage load(@NonNull MessageId messageId) {
    Stopwatch       stopwatch = new Stopwatch("load(" + messageId + "), thread " + threadId);
    MessageDatabase database  = messageId.isMms() ? DatabaseFactory.getMmsDatabase(context) : DatabaseFactory.getSmsDatabase(context);
    MessageRecord   record    = database.getMessageRecordOrNull(messageId.getId());

    stopwatch.split("message");

    try {
      if (record != null) {
        List<Mention> mentions;
        if (messageId.isMms()) {
          mentions = DatabaseFactory.getMentionDatabase(context).getMentionsForMessage(messageId.getId());
        } else {
          mentions = Collections.emptyList();
        }

        stopwatch.split("mentions");

        if (messageId.isMms()) {
          List<DatabaseAttachment> attachments = DatabaseFactory.getAttachmentDatabase(context).getAttachmentsForMessage(messageId.getId());
          if (attachments.size() > 0) {
            record = ((MediaMmsMessageRecord) record).withAttachments(context, attachments);
          }
        }

        stopwatch.split("attachments");

        return ConversationMessage.ConversationMessageFactory.createWithUnresolvedData(ApplicationDependencies.getApplication(), record, mentions);
      } else {
        return null;
      }
    } finally {
      stopwatch.stop(TAG);
    }
  }

  @Override
  public @NonNull MessageId getKey(@NonNull ConversationMessage conversationMessage) {
    return new MessageId(conversationMessage.getMessageRecord().getId(), conversationMessage.getMessageRecord().isMms());
  }

  private static class MentionHelper {

    private Collection<Long>         messageIds          = new LinkedList<>();
    private Map<Long, List<Mention>> messageIdToMentions = new HashMap<>();

    void add(MessageRecord record) {
      if (record.isMms()) {
        messageIds.add(record.getId());
      }
    }

    void fetchMentions(Context context) {
      messageIdToMentions = DatabaseFactory.getMentionDatabase(context).getMentionsForMessages(messageIds);
    }

    @Nullable
    List<Mention> getMentions(long id) {
      return messageIdToMentions.get(id);
    }
  }

  private static class AttachmentHelper {

    private Collection<Long>                    messageIds             = new LinkedList<>();
    private Map<Long, List<DatabaseAttachment>> messageIdToAttachments = new HashMap<>();

    void add(MessageRecord record) {
      if (record.isMms()) {
        messageIds.add(record.getId());
      }
    }

    void fetchAttachments(Context context) {
      messageIdToAttachments = DatabaseFactory.getAttachmentDatabase(context).getAttachmentsForMessages(messageIds);
    }

    @NonNull List<MessageRecord> buildUpdatedModels(@NonNull Context context, @NonNull List<MessageRecord> records) {
      return records.stream()
                    .map(record -> {
                      if (record instanceof MediaMmsMessageRecord) {
                        List<DatabaseAttachment> attachments = messageIdToAttachments.get(record.getId());

                        if (Util.hasItems(attachments)) {
                          return ((MediaMmsMessageRecord) record).withAttachments(context, attachments);
                        }
                      }

                      return record;
                    })
                    .collect(Collectors.toList());
    }
  }
}