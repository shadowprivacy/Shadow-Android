package su.sres.securesms.database;

import androidx.annotation.NonNull;

import su.sres.securesms.logging.Log;

import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.util.LRUCache;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class EarlyReceiptCache {

  private static final String TAG = EarlyReceiptCache.class.getSimpleName();

  private final LRUCache<Long, Map<RecipientId, Long>> cache = new LRUCache<>(100);
  private final String name;

  public EarlyReceiptCache(@NonNull String name) {
    this.name = name;
  }

  public synchronized void increment(long timestamp, @NonNull RecipientId origin) {
    Log.i(TAG, String.format(Locale.US, "[%s] Timestamp: %d, Recipient: %s", name, timestamp, origin.serialize()));

    Map<RecipientId, Long> receipts = cache.get(timestamp);

    if (receipts == null) {
      receipts = new HashMap<>();
    }

    Long count = receipts.get(origin);

    if (count != null) {
      receipts.put(origin, ++count);
    } else {
      receipts.put(origin, 1L);
    }

    cache.put(timestamp, receipts);
  }

  public synchronized Map<RecipientId, Long> remove(long timestamp) {
    Map<RecipientId, Long> receipts = cache.remove(timestamp);

    Log.i(TAG, this+"");
    Log.i(TAG, String.format(Locale.US, "Checking early receipts (%d): %d", timestamp, receipts == null ? 0 : receipts.size()));

    return receipts != null ? receipts : new HashMap<>();
  }
}
