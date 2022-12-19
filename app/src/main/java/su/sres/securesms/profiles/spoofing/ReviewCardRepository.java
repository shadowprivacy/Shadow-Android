package su.sres.securesms.profiles.spoofing;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Stream;

import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.ThreadDatabase;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.groups.GroupChangeException;
import su.sres.securesms.groups.GroupId;
import su.sres.securesms.groups.GroupManager;
import su.sres.securesms.jobs.MultiDeviceMessageRequestResponseJob;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.recipients.RecipientUtil;
import su.sres.securesms.util.TextSecurePreferences;
import su.sres.securesms.util.concurrent.SignalExecutors;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

class ReviewCardRepository {

    private final Context     context;
    private final GroupId.V2  groupId;
    private final RecipientId recipientId;

    protected ReviewCardRepository(@NonNull Context context,
                                   @NonNull GroupId.V2 groupId)
    {
        this.context     = context;
        this.groupId     = groupId;
        this.recipientId = null;
    }

    protected ReviewCardRepository(@NonNull Context context,
                                   @NonNull RecipientId recipientId)
    {
        this.context     = context;
        this.groupId     = null;
        this.recipientId = recipientId;
    }

    void loadRecipients(@NonNull OnRecipientsLoadedListener onRecipientsLoadedListener) {
        if (groupId != null) {
            loadRecipientsForGroup(groupId, onRecipientsLoadedListener);
        } else if (recipientId != null) {
            loadSimilarRecipients(context, recipientId, onRecipientsLoadedListener);
        } else {
            throw new AssertionError();
        }
    }

    @WorkerThread
    int loadGroupsInCommonCount(@NonNull ReviewRecipient reviewRecipient) {
        return ReviewUtil.getGroupsInCommonCount(context, reviewRecipient.getRecipient().getId());
    }

    void block(@NonNull ReviewCard reviewCard, @NonNull Runnable onActionCompleteListener) {
        if (recipientId == null) {
            throw new UnsupportedOperationException();
        }

        SignalExecutors.BOUNDED.execute(() -> {
            RecipientUtil.blockNonGroup(context, reviewCard.getReviewRecipient());
            onActionCompleteListener.run();
        });
    }

    void delete(@NonNull ReviewCard reviewCard, @NonNull Runnable onActionCompleteListener) {
        if (recipientId == null) {
            throw new UnsupportedOperationException();
        }

        SignalExecutors.BOUNDED.execute(() -> {
            Recipient resolved = Recipient.resolved(recipientId);

            if (resolved.isGroup()) throw new AssertionError();

            if (TextSecurePreferences.isMultiDevice(context)) {
                ApplicationDependencies.getJobManager().add(MultiDeviceMessageRequestResponseJob.forDelete(recipientId));
            }

            ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(context);
            long           threadId       = Objects.requireNonNull(threadDatabase.getThreadIdFor(recipientId));

            threadDatabase.deleteConversation(threadId);
            onActionCompleteListener.run();
        });
    }

    void removeFromGroup(@NonNull ReviewCard reviewCard, @NonNull OnRemoveFromGroupListener onRemoveFromGroupListener) {
        if (groupId == null) {
            throw new UnsupportedOperationException();
        }

        SignalExecutors.BOUNDED.execute(() -> {
            try {
                GroupManager.ejectFromGroup(context, groupId, reviewCard.getReviewRecipient());
                onRemoveFromGroupListener.onActionCompleted();
            } catch (GroupChangeException | IOException e) {
                onRemoveFromGroupListener.onActionFailed();
            }
        });
    }

    private static void loadRecipientsForGroup(@NonNull GroupId.V2 groupId,
                                               @NonNull OnRecipientsLoadedListener onRecipientsLoadedListener)
    {
        SignalExecutors.BOUNDED.execute(() -> onRecipientsLoadedListener.onRecipientsLoaded(ReviewUtil.getDuplicatedRecipients(groupId)));
    }

    private static void loadSimilarRecipients(@NonNull Context context,
                                              @NonNull RecipientId recipientId,
                                              @NonNull OnRecipientsLoadedListener onRecipientsLoadedListener)
    {
        SignalExecutors.BOUNDED.execute(() -> {
            Recipient resolved = Recipient.resolved(recipientId);

            List<RecipientId> recipientIds = DatabaseFactory.getRecipientDatabase(context)
                    .getSimilarRecipientIds(resolved);

            if (recipientIds.isEmpty()) {
                onRecipientsLoadedListener.onRecipientsLoadFailed();
                return;
            }

            List<ReviewRecipient> recipients = Stream.of(recipientIds)
                    .map(Recipient::resolved)
                    .map(ReviewRecipient::new)
                    .sorted(new ReviewRecipient.Comparator(context, recipientId))
                    .toList();

            onRecipientsLoadedListener.onRecipientsLoaded(recipients);
        });
    }

    interface OnRecipientsLoadedListener {
        void onRecipientsLoaded(@NonNull List<ReviewRecipient> recipients);
        void onRecipientsLoadFailed();
    }

    interface OnRemoveFromGroupListener {
        void onActionCompleted();
        void onActionFailed();
    }
}
