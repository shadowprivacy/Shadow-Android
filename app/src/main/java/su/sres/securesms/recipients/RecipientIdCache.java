package su.sres.securesms.recipients;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import su.sres.core.util.logging.Log;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Thread safe cache that allows faster looking up of {@link RecipientId}s without hitting the database.
 */
final class RecipientIdCache {

    private static final int INSTANCE_CACHE_LIMIT = 1000;

    static final RecipientIdCache INSTANCE = new RecipientIdCache(INSTANCE_CACHE_LIMIT);

    private static final String TAG = Log.tag(RecipientIdCache.class);

    private final Map<Object, RecipientId> ids;

    RecipientIdCache(int limit) {
        ids = new LinkedHashMap<Object, RecipientId>(128, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Entry<Object, RecipientId> eldest) {
                return size() > limit;
            }
        };
    }

    synchronized void put(@NonNull Recipient recipient) {
        RecipientId      recipientId = recipient.getId();
        Optional<String> userLogin        = recipient.getE164();
        Optional<UUID>   uuid        = recipient.getUuid();

        if (userLogin.isPresent()) {
            ids.put(userLogin.get(), recipientId);
        }

        if (uuid.isPresent()) {
            ids.put(uuid.get(), recipientId);
        }
    }

    synchronized @Nullable RecipientId get(@Nullable UUID uuid, @Nullable String userLogin) {
        if (uuid != null && userLogin != null) {
            RecipientId recipientIdByUuid = ids.get(uuid);
            if (recipientIdByUuid == null) return null;

            RecipientId recipientIdByE164 = ids.get(userLogin);
            if (recipientIdByE164 == null) return null;

            if (recipientIdByUuid.equals(recipientIdByE164)) {
                return recipientIdByUuid;
            } else {
                ids.remove(uuid);
                ids.remove(userLogin);
                Log.w(TAG, "Seen invalid RecipientIdCacheState");
                return null;
            }
        } else if (uuid != null) {
            return ids.get(uuid);
        } else if (userLogin != null) {
            return ids.get(userLogin);
        }

        return null;
    }

    synchronized void clear() {
        ids.clear();
    }
}