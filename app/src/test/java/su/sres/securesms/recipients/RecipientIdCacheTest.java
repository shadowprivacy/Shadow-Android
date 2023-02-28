package su.sres.securesms.recipients;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Before;
import org.junit.Test;
import su.sres.core.util.logging.Log;
import su.sres.securesms.testutil.LogRecorder;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class RecipientIdCacheTest {

    private static final int TEST_CACHE_LIMIT = 5;

    private RecipientIdCache recipientIdCache;
    private LogRecorder      logRecorder;

    @Before
    public void setup() {
        recipientIdCache = new RecipientIdCache(TEST_CACHE_LIMIT);
        logRecorder      = new LogRecorder();
        Log.initialize(logRecorder);
    }

    @Test
    public void empty_access_by_nulls() {
        RecipientId recipientId = recipientIdCache.get(null, null);

        assertNull(recipientId);
    }

    @Test
    public void empty_access_by_uuid() {
        RecipientId recipientId = recipientIdCache.get(UUID.randomUUID(), null);

        assertNull(recipientId);
    }

    @Test
    public void empty_access_by_userLogin() {
        RecipientId recipientId = recipientIdCache.get(null, "+155512345");

        assertNull(recipientId);
    }

    @Test
    public void cache_hit_by_uuid() {
        RecipientId recipientId1 = recipientId();
        UUID        uuid1        = UUID.randomUUID();

        recipientIdCache.put(recipient(recipientId1, uuid1, null));

        RecipientId recipientId = recipientIdCache.get(uuid1, null);

        assertEquals(recipientId1, recipientId);
    }

    @Test
    public void cache_miss_by_uuid() {
        RecipientId recipientId1 = recipientId();
        UUID        uuid1        = UUID.randomUUID();
        UUID        uuid2        = UUID.randomUUID();

        recipientIdCache.put(recipient(recipientId1, uuid1, null));

        RecipientId recipientId = recipientIdCache.get(uuid2, null);

        assertNull(recipientId);
    }

    @Test
    public void cache_hit_by_uuid_userLogin_not_supplied_on_get() {
        RecipientId recipientId1 = recipientId();
        UUID        uuid1        = UUID.randomUUID();

        recipientIdCache.put(recipient(recipientId1, uuid1, "+15551234567"));

        RecipientId recipientId = recipientIdCache.get(uuid1, null);

        assertEquals(recipientId1, recipientId);
    }

    @Test
    public void cache_miss_by_uuid_userLogin_not_supplied_on_put() {
        RecipientId recipientId1 = recipientId();
        UUID        uuid1        = UUID.randomUUID();

        recipientIdCache.put(recipient(recipientId1, uuid1, null));

        RecipientId recipientId = recipientIdCache.get(uuid1, "+15551234567");

        assertNull(recipientId);
    }

    @Test
    public void cache_hit_by_userLogin() {
        RecipientId recipientId1 = recipientId();
        String      userLogin         = "+1555123456";

        recipientIdCache.put(recipient(recipientId1, null, userLogin));

        RecipientId recipientId = recipientIdCache.get(null, userLogin);

        assertEquals(recipientId1, recipientId);
    }

    @Test
    public void cache_miss_by_userLogin() {
        RecipientId recipientId1 = recipientId();
        String      userLogina        = "+1555123456";
        String      userLoginb        = "+1555123457";

        recipientIdCache.put(recipient(recipientId1, null, userLogina));

        RecipientId recipientId = recipientIdCache.get(null, userLoginb);

        assertNull(recipientId);
    }

    @Test
    public void cache_hit_by_userLogin_uuid_not_supplied_on_get() {
        RecipientId recipientId1 = recipientId();
        UUID        uuid1        = UUID.randomUUID();

        recipientIdCache.put(recipient(recipientId1, uuid1, "+15551234567"));

        RecipientId recipientId = recipientIdCache.get(null, "+15551234567");

        assertEquals(recipientId1, recipientId);
    }

    @Test
    public void cache_miss_by_userLogin_uuid_not_supplied_on_put() {
        RecipientId recipientId1 = recipientId();
        UUID        uuid1        = UUID.randomUUID();
        String      userLogin         = "+1555123456";

        recipientIdCache.put(recipient(recipientId1, null, userLogin));

        RecipientId recipientId = recipientIdCache.get(uuid1, userLogin);

        assertNull(recipientId);
    }

    @Test
    public void cache_hit_by_both() {
        RecipientId recipientId1 = recipientId();
        UUID        uuid1        = UUID.randomUUID();
        String      userLogin         = "+1555123456";

        recipientIdCache.put(recipient(recipientId1, uuid1, userLogin));

        RecipientId recipientId = recipientIdCache.get(uuid1, userLogin);

        assertEquals(recipientId1, recipientId);
    }

    @Test
    public void full_recipient_id_learned_by_two_puts() {
        RecipientId recipientId1 = recipientId();
        UUID        uuid1        = UUID.randomUUID();
        String      userLogin         = "+1555123456";

        recipientIdCache.put(recipient(recipientId1, uuid1, null));
        recipientIdCache.put(recipient(recipientId1, null, userLogin));

        RecipientId recipientId = recipientIdCache.get(uuid1, userLogin);

        assertEquals(recipientId1, recipientId);
    }

    @Test
    public void if_cache_state_disagrees_returns_null() {
        RecipientId recipientId1 = recipientId();
        RecipientId recipientId2 = recipientId();
        UUID        uuid         = UUID.randomUUID();
        String      userLogin         = "+1555123456";

        recipientIdCache.put(recipient(recipientId1, null, userLogin));
        recipientIdCache.put(recipient(recipientId2, uuid, null));

        RecipientId recipientId = recipientIdCache.get(uuid, userLogin);

        assertNull(recipientId);

        assertEquals(1, logRecorder.getWarnings().size());
        assertEquals("Seen invalid RecipientIdCacheState", logRecorder.getWarnings().get(0).getMessage());
    }

    @Test
    public void after_invalid_cache_hit_entries_are_cleared_up() {
        RecipientId recipientId1 = recipientId();
        RecipientId recipientId2 = recipientId();
        UUID        uuid         = UUID.randomUUID();
        String      userLogin         = "+1555123456";

        recipientIdCache.put(recipient(recipientId1, null, userLogin));
        recipientIdCache.put(recipient(recipientId2, uuid, null));

        recipientIdCache.get(uuid, userLogin);

        assertNull(recipientIdCache.get(uuid, null));
        assertNull(recipientIdCache.get(null, userLogin));
    }

    @Test
    public void multiple_entries() {
        RecipientId recipientId1 = recipientId();
        RecipientId recipientId2 = recipientId();
        UUID        uuid1        = UUID.randomUUID();
        UUID        uuid2        = UUID.randomUUID();

        recipientIdCache.put(recipient(recipientId1, uuid1, null));
        recipientIdCache.put(recipient(recipientId2, uuid2, null));

        assertEquals(recipientId1, recipientIdCache.get(uuid1, null));
        assertEquals(recipientId2, recipientIdCache.get(uuid2, null));
    }

    @Test
    public void drops_oldest_when_reaches_cache_limit() {
        RecipientId recipientId1 = recipientId();
        UUID        uuid1        = UUID.randomUUID();

        recipientIdCache.put(recipient(recipientId1, uuid1, null));

        for (int i = 0; i < TEST_CACHE_LIMIT; i++) {
            recipientIdCache.put(recipient(recipientId(), UUID.randomUUID(), null));
        }

        assertNull(recipientIdCache.get(uuid1, null));
    }

    @Test
    public void remains_in_cache_when_used_before_reaching_cache_limit() {
        RecipientId recipientId1 = recipientId();
        UUID        uuid1        = UUID.randomUUID();

        recipientIdCache.put(recipient(recipientId1, uuid1, null));

        for (int i = 0; i < TEST_CACHE_LIMIT - 1; i++) {
            recipientIdCache.put(recipient(recipientId(), UUID.randomUUID(), null));
        }

        assertEquals(recipientId1, recipientIdCache.get(uuid1, null));

        recipientIdCache.put(recipient(recipientId(), UUID.randomUUID(), null));

        assertEquals(recipientId1, recipientIdCache.get(uuid1, null));
    }

    private static @NonNull RecipientId recipientId() {
        return mock(RecipientId.class);
    }

    private static @NonNull Recipient recipient(RecipientId recipientId, @Nullable UUID uuid, @Nullable String userLogin) {
        Recipient mock = mock(Recipient.class);

        when(mock.getId()).thenReturn(recipientId);
        when(mock.getUuid()).thenReturn(Optional.fromNullable(uuid));
        when(mock.getE164()).thenReturn(Optional.fromNullable(userLogin));

        return mock;
    }
}