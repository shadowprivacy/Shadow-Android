package su.sres.securesms.blocked;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;

import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.RecipientDatabase;
import su.sres.securesms.groups.GroupChangeBusyException;
import su.sres.securesms.groups.GroupChangeFailedException;
import su.sres.core.util.logging.Log;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.recipients.RecipientUtil;
import su.sres.core.util.concurrent.SignalExecutors;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class BlockedUsersRepository {

    private static final String TAG = Log.tag(BlockedUsersRepository.class);

    private final Context context;

    BlockedUsersRepository(@NonNull Context context) {
        this.context = context;
    }

    void getBlocked(@NonNull Consumer<List<Recipient>> blockedUsers) {
        SignalExecutors.BOUNDED.execute(() -> {
            RecipientDatabase db = DatabaseFactory.getRecipientDatabase(context);
            try (RecipientDatabase.RecipientReader reader = db.readerForBlocked(db.getBlocked())) {
                int count = reader.getCount();
                if (count == 0) {
                    blockedUsers.accept(Collections.emptyList());
                } else {
                    List<Recipient> recipients = new ArrayList<>();
                    while (reader.getNext() != null) {
                        recipients.add(reader.getCurrent());
                    }
                    blockedUsers.accept(recipients);
                }
            }
        });
    }

    void block(@NonNull RecipientId recipientId, @NonNull Runnable success, @NonNull Runnable failure) {
        SignalExecutors.BOUNDED.execute(() -> {
            try {
                RecipientUtil.block(context, Recipient.resolved(recipientId));
                success.run();
            } catch (IOException | GroupChangeFailedException | GroupChangeBusyException e) {
                Log.w(TAG, "block: failed to block recipient: ", e);
                failure.run();
            }
        });
    }

    void createAndBlock(@NonNull String number, @NonNull Runnable success) {
        SignalExecutors.BOUNDED.execute(() -> {
            RecipientUtil.blockNonGroup(context, Recipient.external(context, number));
            success.run();
        });
    }

    void unblock(@NonNull RecipientId recipientId, @NonNull Runnable success) {
        SignalExecutors.BOUNDED.execute(() -> {
            RecipientUtil.unblock(context, Recipient.resolved(recipientId));
            success.run();
        });
    }
}
