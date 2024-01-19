package su.sres.securesms.conversation;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import su.sres.paging.PagedDataSource;
import su.sres.securesms.conversation.ConversationData.MessageRequestData;
import su.sres.securesms.conversation.ConversationMessage.ConversationMessageFactory;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.MmsSmsDatabase;
import su.sres.securesms.database.model.InMemoryMessageRecord;
import su.sres.securesms.database.model.Mention;
import su.sres.securesms.database.model.MessageRecord;
import su.sres.core.util.logging.Log;
import su.sres.securesms.util.Stopwatch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Core data source for loading an individual conversation.
 */
class ConversationDataSource implements PagedDataSource<ConversationMessage> {

    private static final String TAG = Log.tag(ConversationDataSource.class);

    private final Context             context;
    private final long                threadId;
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
        int  size      = DatabaseFactory.getMmsSmsDatabase(context).getConversationCount(threadId) +
                         (messageRequestData.includeWarningUpdateMessage() ? 1 : 0) +
                         (showUniversalExpireTimerUpdate ? 1 : 0);

        Log.d(TAG, "size() for thread " + threadId + ": " + (System.currentTimeMillis() - startTime) + " ms");

        return size;
    }

    @Override
    public @NonNull List<ConversationMessage> load(int start, int length, @NonNull CancellationSignal cancellationSignal) {
        Stopwatch stopwatch     = new Stopwatch("load(" + start + ", " + length + "), thread " + threadId);

        MmsSmsDatabase      db            = DatabaseFactory.getMmsSmsDatabase(context);
        List<MessageRecord> records       = new ArrayList<>(length);
        MentionHelper       mentionHelper = new MentionHelper();

        try (MmsSmsDatabase.Reader reader = MmsSmsDatabase.readerFor(db.getConversation(threadId, start, length))) {
            MessageRecord record;
            while ((record = reader.getNext()) != null && !cancellationSignal.isCanceled()) {
                records.add(record);
                mentionHelper.add(record);
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

        List<ConversationMessage> messages = Stream.of(records)
                .map(m -> ConversationMessageFactory.createWithUnresolvedData(context, m, mentionHelper.getMentions(m.getId())))
                .toList();

        stopwatch.split("conversion");
        stopwatch.stop(TAG);

        return messages;
    }

    private static class MentionHelper {

        private Collection<Long> messageIds          = new LinkedList<>();
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
}