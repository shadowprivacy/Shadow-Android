package su.sres.securesms.database.helpers;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.google.protobuf.InvalidProtocolBufferException;

import su.sres.securesms.color.MaterialColor;
import su.sres.securesms.conversation.colors.AvatarColor;
import su.sres.securesms.conversation.colors.ChatColors;
import su.sres.securesms.conversation.colors.ChatColorsMapper;
import su.sres.securesms.database.ChatColorsDatabase;
import su.sres.securesms.database.EmojiSearchDatabase;
import su.sres.securesms.database.GroupCallRingDatabase;
import su.sres.securesms.database.MentionDatabase;
import su.sres.securesms.database.MessageSendLogDatabase;
import su.sres.securesms.database.PaymentDatabase;
import su.sres.securesms.database.PendingRetryReceiptDatabase;
import su.sres.securesms.database.SenderKeyDatabase;
import su.sres.securesms.database.SenderKeySharedDatabase;
import su.sres.securesms.database.SignalDatabase;
import su.sres.securesms.database.SqlCipherDatabaseHook;
import su.sres.securesms.database.SqlCipherErrorHandler;
import su.sres.securesms.database.model.AvatarPickerDatabase;

import su.sres.securesms.database.model.databaseprotos.ReactionList;
import su.sres.securesms.groups.GroupId;
import su.sres.core.util.logging.Log;

import net.zetetic.database.sqlcipher.SQLiteDatabase;
import net.zetetic.database.sqlcipher.SQLiteOpenHelper;

import su.sres.securesms.crypto.DatabaseSecret;
import su.sres.securesms.database.AttachmentDatabase;
import su.sres.securesms.database.DraftDatabase;
import su.sres.securesms.database.GroupDatabase;
import su.sres.securesms.database.GroupReceiptDatabase;
import su.sres.securesms.database.IdentityDatabase;
import su.sres.securesms.database.MmsDatabase;
import su.sres.securesms.database.OneTimePreKeyDatabase;
import su.sres.securesms.database.PushDatabase;
import su.sres.securesms.database.RecipientDatabase;
import su.sres.securesms.database.SearchDatabase;
import su.sres.securesms.database.SessionDatabase;
import su.sres.securesms.database.SignedPreKeyDatabase;
import su.sres.securesms.database.SmsDatabase;
import su.sres.securesms.database.StickerDatabase;
import su.sres.securesms.database.UnknownStorageIdDatabase;
import su.sres.securesms.database.ThreadDatabase;
import su.sres.securesms.storage.StorageSyncHelper;
import su.sres.securesms.util.Base64;
import su.sres.securesms.util.CursorUtil;
import su.sres.securesms.util.Hex;
import su.sres.securesms.util.SqlUtil;
import su.sres.securesms.util.Stopwatch;
import su.sres.securesms.util.Triple;
import su.sres.securesms.util.Util;
import su.sres.signalservice.api.push.DistributionId;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class SQLCipherOpenHelper extends SQLiteOpenHelper implements SignalDatabase {

  @SuppressWarnings("unused")
  private static final String TAG = Log.tag(SQLCipherOpenHelper.class);

  private static final int SERVER_DELIVERED_TIMESTAMP                                                             = 64;
  private static final int QUOTE_CLEANUP                                                                          = 65;
  private static final int BORDERLESS                                                                             = 66;
  private static final int MENTIONS                                                                               = 67;
  private static final int PINNED_CONVERSATIONS_MENTION_GLOBAL_SETTING_MIGRATION_UNKNOWN_STORAGE_FIELDS           = 68;
  private static final int STICKER_CONTENT_TYPE_EMOJI_IN_NOTIFICATIONS                                            = 69;
  private static final int THUMBNAIL_CLEANUP_AND_STICKER_CONTENT_TYPE_CLEANUP_AND_MENTION_CLEANUP                 = 70;
  private static final int REACTION_CLEANUP                                                                       = 71;
  private static final int CAPABILITIES_REFACTOR_AND_GV1_MIGRATION                                                = 72;
  private static final int NOTIFIED_TIMESTAMP_AND_GV1_MIGRATION_LAST_SEEN                                         = 73;
  private static final int VIEWED_RECEIPTS_CLEAN_UP_GV1_IDS                                                       = 74;
  private static final int GV1_MIGRATION_REFACTOR                                                                 = 75;
  private static final int CLEAR_PROFILE_KEY_CREDENTIALS                                                          = 76;
  private static final int LAST_RESET_SESSION_TIME_AND_WALLPAPER_AND_ABOUT                                        = 77;
  private static final int SPLIT_SYSTEM_NAMES                                                                     = 78;
  private static final int PAYMENTS_AND_CLEAN_STORAGE_IDS                                                         = 79;
  private static final int MP4_GIF_SUPPORT_AND_BLUR_AVATARS_AND_CLEAN_STORAGE_IDS_WITHOUT_INFO                    = 80;
  private static final int CLEAN_REACTION_NOTIFICATIONS                                                           = 81;
  private static final int STORAGE_SERVICE_REFACTOR                                                               = 82;
  private static final int CLEAR_MMS_STORAGE_IDS                                                                  = 83;
  private static final int SERVER_GUID                                                                            = 84;
  private static final int CHAT_COLORS_AND_AVATAR_COLORS_AND_EMOJI_SEARCH                                         = 85;
  private static final int SENDER_KEY                                                                             = 86;
  private static final int MESSAGE_DUPE_INDEX_AND_MESSAGE_LOG                                                     = 87;
  private static final int MESSAGE_LOG_2                                                                          = 88;
  private static final int ABANDONED_MESSAGE_CLEANUP                                                              = 89;
  private static final int THREAD_AUTOINCREMENT_AND_MMS_AUTOINCREMENT_AND_ABANDONED_ATTACHMENT_CLEANUP            = 90;
  private static final int AVATAR_PICKER                                                                          = 91;
  private static final int THREAD_CLEANUP                                                                         = 92;
  private static final int SESSION_AND_IDENTITY_MIGRATION_AND_GROUP_CALL_RING_TABLE_AND_CLEANUP_SESSION_MIGRATION = 93;
  private static final int RECEIPT_TIMESTAMP                                                                      = 94;
  private static final int BADGES                                                                                 = 95;
  private static final int SENDER_KEY_UUID                  = 96;

  private static final int    DATABASE_VERSION = 96;
  private static final String DATABASE_NAME    = "shadow.db";

  private final Context context;

  public SQLCipherOpenHelper(@NonNull Context context, @NonNull DatabaseSecret databaseSecret) {
    super(context, DATABASE_NAME, databaseSecret.asString(), null, DATABASE_VERSION, 0, new SqlCipherErrorHandler(DATABASE_NAME), new SqlCipherDatabaseHook());

    this.context = context.getApplicationContext();
  }

  @Override
  public void onOpen(SQLiteDatabase db) {
    db.enableWriteAheadLogging();
    db.setForeignKeyConstraintsEnabled(true);
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    db.execSQL(SmsDatabase.CREATE_TABLE);
    db.execSQL(MmsDatabase.CREATE_TABLE);
    db.execSQL(AttachmentDatabase.CREATE_TABLE);
    db.execSQL(ThreadDatabase.CREATE_TABLE);
    db.execSQL(IdentityDatabase.CREATE_TABLE);
    db.execSQL(DraftDatabase.CREATE_TABLE);
    db.execSQL(PushDatabase.CREATE_TABLE);
    db.execSQL(GroupDatabase.CREATE_TABLE);
    db.execSQL(RecipientDatabase.CREATE_TABLE);
    db.execSQL(GroupReceiptDatabase.CREATE_TABLE);
    db.execSQL(OneTimePreKeyDatabase.CREATE_TABLE);
    db.execSQL(SignedPreKeyDatabase.CREATE_TABLE);
    db.execSQL(SessionDatabase.CREATE_TABLE);
    db.execSQL(SenderKeyDatabase.CREATE_TABLE);
    db.execSQL(SenderKeySharedDatabase.CREATE_TABLE);
    db.execSQL(PendingRetryReceiptDatabase.CREATE_TABLE);
    db.execSQL(StickerDatabase.CREATE_TABLE);
    db.execSQL(UnknownStorageIdDatabase.CREATE_TABLE);
    db.execSQL(MentionDatabase.CREATE_TABLE);
    db.execSQL(PaymentDatabase.CREATE_TABLE);
    db.execSQL(ChatColorsDatabase.CREATE_TABLE);
    db.execSQL(EmojiSearchDatabase.CREATE_TABLE);
    db.execSQL(AvatarPickerDatabase.CREATE_TABLE);
    db.execSQL(GroupCallRingDatabase.CREATE_TABLE);

    executeStatements(db, SearchDatabase.CREATE_TABLE);
    executeStatements(db, MessageSendLogDatabase.CREATE_TABLE);

    executeStatements(db, RecipientDatabase.CREATE_INDEXS);
    executeStatements(db, SmsDatabase.CREATE_INDEXS);
    executeStatements(db, MmsDatabase.CREATE_INDEXS);
    executeStatements(db, AttachmentDatabase.CREATE_INDEXS);
    executeStatements(db, ThreadDatabase.CREATE_INDEXS);
    executeStatements(db, DraftDatabase.CREATE_INDEXS);
    executeStatements(db, GroupDatabase.CREATE_INDEXS);
    executeStatements(db, GroupReceiptDatabase.CREATE_INDEXES);
    executeStatements(db, StickerDatabase.CREATE_INDEXES);
    executeStatements(db, UnknownStorageIdDatabase.CREATE_INDEXES);
    executeStatements(db, MentionDatabase.CREATE_INDEXES);
    executeStatements(db, PaymentDatabase.CREATE_INDEXES);
    executeStatements(db, MessageSendLogDatabase.CREATE_INDEXES);
    executeStatements(db, GroupCallRingDatabase.CREATE_INDEXES);

    executeStatements(db, MessageSendLogDatabase.CREATE_TRIGGERS);
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    Log.i(TAG, "Upgrading database: " + oldVersion + ", " + newVersion);
    long startTime = System.currentTimeMillis();

    db.beginTransaction();

    try {

      if (oldVersion < SERVER_DELIVERED_TIMESTAMP) {
        db.execSQL("ALTER TABLE thread ADD COLUMN last_scrolled INTEGER DEFAULT 0");
        db.execSQL("ALTER TABLE recipient ADD COLUMN last_profile_fetch INTEGER DEFAULT 0");
        db.execSQL("ALTER TABLE push ADD COLUMN server_delivered_timestamp INTEGER DEFAULT 0");
      }

      if (oldVersion < QUOTE_CLEANUP) {
        String query = "SELECT _data " +
                       "FROM (SELECT _data, MIN(quote) AS all_quotes " +
                       "FROM part " +
                       "WHERE _data NOT NULL AND data_hash NOT NULL " +
                       "GROUP BY _data) " +
                       "WHERE all_quotes = 1";

        int count = 0;

        try (Cursor cursor = db.rawQuery(query, null)) {
          while (cursor != null && cursor.moveToNext()) {
            String data = cursor.getString(cursor.getColumnIndexOrThrow("_data"));

            if (new File(data).delete()) {
              ContentValues values = new ContentValues();
              values.putNull("_data");
              values.putNull("data_random");
              values.putNull("thumbnail");
              values.putNull("thumbnail_random");
              values.putNull("data_hash");
              db.update("part", values, "_data = ?", new String[] { data });

              count++;
            } else {
              Log.w(TAG, "[QuoteCleanup] Failed to delete " + data);
            }
          }
        }

        Log.i(TAG, "[QuoteCleanup] Cleaned up " + count + " quotes.");
      }

      if (oldVersion < BORDERLESS) {
        db.execSQL("ALTER TABLE part ADD COLUMN borderless INTEGER DEFAULT 0");
      }

      if (oldVersion < MENTIONS) {
        db.execSQL("CREATE TABLE mention (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                   "thread_id INTEGER, " +
                   "message_id INTEGER, " +
                   "recipient_id INTEGER, " +
                   "range_start INTEGER, " +
                   "range_length INTEGER)");

        db.execSQL("CREATE INDEX IF NOT EXISTS mention_message_id_index ON mention (message_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS mention_recipient_id_thread_id_index ON mention (recipient_id, thread_id);");

        db.execSQL("ALTER TABLE mms ADD COLUMN quote_mentions BLOB DEFAULT NULL");
        db.execSQL("ALTER TABLE mms ADD COLUMN mentions_self INTEGER DEFAULT 0");

        db.execSQL("ALTER TABLE recipient ADD COLUMN mention_setting INTEGER DEFAULT 0");
      }

      if (oldVersion < PINNED_CONVERSATIONS_MENTION_GLOBAL_SETTING_MIGRATION_UNKNOWN_STORAGE_FIELDS) {
        db.execSQL("ALTER TABLE thread ADD COLUMN pinned INTEGER DEFAULT 0");
        db.execSQL("CREATE INDEX IF NOT EXISTS thread_pinned_index ON thread (pinned)");

        ContentValues updateAlways = new ContentValues();
        updateAlways.put("mention_setting", 0);
        db.update("recipient", updateAlways, "mention_setting = 1", null);

        ContentValues updateNever = new ContentValues();
        updateNever.put("mention_setting", 1);
        db.update("recipient", updateNever, "mention_setting = 2", null);

        db.execSQL("ALTER TABLE recipient ADD COLUMN storage_proto TEXT DEFAULT NULL");
      }

      if (oldVersion < STICKER_CONTENT_TYPE_EMOJI_IN_NOTIFICATIONS) {
        db.execSQL("ALTER TABLE sticker ADD COLUMN content_type TEXT DEFAULT NULL");
        db.execSQL("ALTER TABLE part ADD COLUMN sticker_emoji TEXT DEFAULT NULL");
      }

      if (oldVersion < THUMBNAIL_CLEANUP_AND_STICKER_CONTENT_TYPE_CLEANUP_AND_MENTION_CLEANUP) {
        int total   = 0;
        int deleted = 0;

        try (Cursor cursor = db.rawQuery("SELECT thumbnail FROM part WHERE thumbnail NOT NULL", null)) {
          if (cursor != null) {
            total = cursor.getCount();
            Log.w(TAG, "Found " + total + " thumbnails to delete.");
          }

          while (cursor != null && cursor.moveToNext()) {
            File file = new File(CursorUtil.requireString(cursor, "thumbnail"));

            if (file.delete()) {
              deleted++;
            } else {
              Log.w(TAG, "Failed to delete file! " + file.getAbsolutePath());
            }
          }
        }

        Log.w(TAG, "Deleted " + deleted + "/" + total + " thumbnail files.");

        // sticker content type cleanup

        ContentValues values = new ContentValues();
        values.put("ct", "image/webp");

        String query = "sticker_id NOT NULL AND (ct IS NULL OR ct = '')";

        int rows = db.update("part", values, query, null);
        Log.i(TAG, "Updated " + rows + " sticker attachment content types.");

        // mention cleanup

        String selectMentionIdsNotInGroupsV2 = "select mention._id from mention left join thread on mention.thread_id = thread._id left join recipient on thread.recipient_ids = recipient._id where recipient.group_type != 3";
        db.delete("mention", "_id in (" + selectMentionIdsNotInGroupsV2 + ")", null);
        db.delete("mention", "message_id NOT IN (SELECT _id FROM mms) OR thread_id NOT IN (SELECT _id from thread)", null);

        List<Long> idsToDelete = new LinkedList<>();
        try (Cursor cursor = db.rawQuery("select mention.*, mms.body from mention inner join mms on mention.message_id = mms._id", null)) {
          while (cursor != null && cursor.moveToNext()) {
            int    rangeStart  = CursorUtil.requireInt(cursor, "range_start");
            int    rangeLength = CursorUtil.requireInt(cursor, "range_length");
            String body        = CursorUtil.requireString(cursor, "body");

            if (body == null || body.isEmpty() || rangeStart < 0 || rangeLength < 0 || (rangeStart + rangeLength) > body.length()) {
              idsToDelete.add(CursorUtil.requireLong(cursor, "_id"));
            }
          }
        }

        if (Util.hasItems(idsToDelete)) {
          String ids = TextUtils.join(",", idsToDelete);
          db.delete("mention", "_id in (" + ids + ")", null);
        }

        String selectMentionIdsWithMismatchingThreadIds = "select mention._id from mention left join mms on mention.message_id = mms._id where mention.thread_id != mms.thread_id";
        db.delete("mention", "_id in (" + selectMentionIdsWithMismatchingThreadIds + ")", null);

        List<Long>                          idsToDelete2  = new LinkedList<>();
        Set<Triple<Long, Integer, Integer>> mentionTuples = new HashSet<>();
        try (Cursor cursor = db.rawQuery("select mention.*, mms.body from mention inner join mms on mention.message_id = mms._id order by mention._id desc", null)) {
          while (cursor != null && cursor.moveToNext()) {
            long   mentionId   = CursorUtil.requireLong(cursor, "_id");
            long   messageId   = CursorUtil.requireLong(cursor, "message_id");
            int    rangeStart  = CursorUtil.requireInt(cursor, "range_start");
            int    rangeLength = CursorUtil.requireInt(cursor, "range_length");
            String body        = CursorUtil.requireString(cursor, "body");

            if (body != null && rangeStart < body.length() && body.charAt(rangeStart) != '\uFFFC') {
              idsToDelete2.add(mentionId);
            } else {
              Triple<Long, Integer, Integer> tuple = new Triple<>(messageId, rangeStart, rangeLength);
              if (mentionTuples.contains(tuple)) {
                idsToDelete2.add(mentionId);
              } else {
                mentionTuples.add(tuple);
              }
            }
          }

          if (Util.hasItems(idsToDelete2)) {
            String ids = TextUtils.join(",", idsToDelete2);
            db.delete("mention", "_id in (" + ids + ")", null);
          }
        }
      }

      if (oldVersion < REACTION_CLEANUP) {
        ContentValues values = new ContentValues();
        values.putNull("reactions");
        db.update("sms", values, "remote_deleted = ?", new String[] { "1" });
      }

      if (oldVersion < CAPABILITIES_REFACTOR_AND_GV1_MIGRATION) {
        db.execSQL("ALTER TABLE recipient ADD COLUMN capabilities INTEGER DEFAULT 0");

        db.execSQL("UPDATE recipient SET capabilities = 1 WHERE gv2_capability = 1");
        db.execSQL("UPDATE recipient SET capabilities = 2 WHERE gv2_capability = -1");

        // gv1 migration

        db.execSQL("ALTER TABLE groups ADD COLUMN expected_v2_id TEXT DEFAULT NULL");
        db.execSQL("ALTER TABLE groups ADD COLUMN former_v1_members TEXT DEFAULT NULL");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS expected_v2_id_index ON groups (expected_v2_id)");

        int count = 0;
        try (Cursor cursor = db.rawQuery("SELECT * FROM groups WHERE group_id LIKE '__textsecure_group__!%' AND LENGTH(group_id) = 53", null)) {
          while (cursor.moveToNext()) {
            String gv1 = CursorUtil.requireString(cursor, "group_id");
            String gv2 = GroupId.parseOrThrow(gv1).requireV1().deriveV2MigrationGroupId().toString();

            ContentValues values = new ContentValues();
            values.put("expected_v2_id", gv2);
            count += db.update("groups", values, "group_id = ?", SqlUtil.buildArgs(gv1));
          }
        }

        Log.i(TAG, "Updated " + count + " GV1 groups with expected GV2 IDs.");
      }

      if (oldVersion < NOTIFIED_TIMESTAMP_AND_GV1_MIGRATION_LAST_SEEN) {
        db.execSQL("ALTER TABLE sms ADD COLUMN notified_timestamp INTEGER DEFAULT 0");
        db.execSQL("ALTER TABLE mms ADD COLUMN notified_timestamp INTEGER DEFAULT 0");

        db.execSQL("ALTER TABLE recipient ADD COLUMN last_gv1_migrate_reminder INTEGER DEFAULT 0");
      }

      if (oldVersion < VIEWED_RECEIPTS_CLEAN_UP_GV1_IDS) {
        db.execSQL("ALTER TABLE mms ADD COLUMN viewed_receipt_count INTEGER DEFAULT 0");
        //
        List<String> deletableRecipients = new LinkedList<>();
        try (Cursor cursor = db.rawQuery("SELECT _id, group_id FROM recipient\n" +
                                         "WHERE group_id NOT IN (SELECT group_id FROM groups)\n" +
                                         "AND group_id LIKE '__textsecure_group__!%' AND length(group_id) <> 53\n" +
                                         "AND (_id NOT IN (SELECT recipient_ids FROM thread) OR _id IN (SELECT recipient_ids FROM thread WHERE message_count = 0))", null))
        {
          while (cursor.moveToNext()) {
            String recipientId = cursor.getString(cursor.getColumnIndexOrThrow("_id"));
            String groupIdV1   = cursor.getString(cursor.getColumnIndexOrThrow("group_id"));
            deletableRecipients.add(recipientId);
            Log.d(TAG, String.format(Locale.US, "Found invalid GV1 on %s with no or empty thread %s length %d", recipientId, groupIdV1, groupIdV1.length()));
          }
        }

        for (String recipientId : deletableRecipients) {
          db.delete("recipient", "_id = ?", new String[] { recipientId });
          Log.d(TAG, "Deleted recipient " + recipientId);
        }

        List<String> orphanedThreads = new LinkedList<>();
        try (Cursor cursor = db.rawQuery("SELECT _id FROM thread WHERE message_count = 0 AND recipient_ids NOT IN (SELECT _id FROM recipient)", null)) {
          while (cursor.moveToNext()) {
            orphanedThreads.add(cursor.getString(cursor.getColumnIndexOrThrow("_id")));
          }
        }

        for (String orphanedThreadId : orphanedThreads) {
          db.delete("thread", "_id = ?", new String[] { orphanedThreadId });
          Log.d(TAG, "Deleted orphaned thread " + orphanedThreadId);
        }

        List<String> remainingInvalidGV1Recipients = new LinkedList<>();
        try (Cursor cursor = db.rawQuery("SELECT _id, group_id FROM recipient\n" +
                                         "WHERE group_id NOT IN (SELECT group_id FROM groups)\n" +
                                         "AND group_id LIKE '__textsecure_group__!%' AND length(group_id) <> 53\n" +
                                         "AND _id IN (SELECT recipient_ids FROM thread)", null))
        {
          while (cursor.moveToNext()) {
            String recipientId = cursor.getString(cursor.getColumnIndexOrThrow("_id"));
            String groupIdV1   = cursor.getString(cursor.getColumnIndexOrThrow("group_id"));
            remainingInvalidGV1Recipients.add(recipientId);
            Log.d(TAG, String.format(Locale.US, "Found invalid GV1 on %s with non-empty thread %s length %d", recipientId, groupIdV1, groupIdV1.length()));
          }
        }

        for (String recipientId : remainingInvalidGV1Recipients) {
          String        newId  = "__textsecure_group__!" + Hex.toStringCondensed(Util.getSecretBytes(16));
          ContentValues values = new ContentValues(1);
          values.put("group_id", newId);

          db.update("recipient", values, "_id = ?", new String[] { String.valueOf(recipientId) });
          Log.d(TAG, String.format("Replaced group id on recipient %s now %s", recipientId, newId));
        }
      }

      if (oldVersion < GV1_MIGRATION_REFACTOR) {
        ContentValues values = new ContentValues(1);
        values.putNull("former_v1_members");

        int count = db.update("groups", values, "former_v1_members NOT NULL", null);

        Log.i(TAG, "Cleared former_v1_members for " + count + " rows");
      }

      if (oldVersion < CLEAR_PROFILE_KEY_CREDENTIALS) {
        ContentValues values = new ContentValues(1);
        values.putNull("profile_key_credential");

        int count = db.update("recipient", values, "profile_key_credential NOT NULL", null);

        Log.i(TAG, "Cleared profile key credentials for " + count + " rows");
      }

      if (oldVersion < LAST_RESET_SESSION_TIME_AND_WALLPAPER_AND_ABOUT) {
        db.execSQL("ALTER TABLE recipient ADD COLUMN last_session_reset BLOB DEFAULT NULL");

        db.execSQL("ALTER TABLE recipient ADD COLUMN wallpaper BLOB DEFAULT NULL");
        db.execSQL("ALTER TABLE recipient ADD COLUMN wallpaper_file TEXT DEFAULT NULL");

        db.execSQL("ALTER TABLE recipient ADD COLUMN about TEXT DEFAULT NULL");
        db.execSQL("ALTER TABLE recipient ADD COLUMN about_emoji TEXT DEFAULT NULL");
      }

      if (oldVersion < SPLIT_SYSTEM_NAMES) {
        db.execSQL("ALTER TABLE recipient ADD COLUMN system_family_name TEXT DEFAULT NULL");
        db.execSQL("ALTER TABLE recipient ADD COLUMN system_given_name TEXT DEFAULT NULL");
        db.execSQL("UPDATE recipient SET system_given_name = system_display_name");
      }

      if (oldVersion < PAYMENTS_AND_CLEAN_STORAGE_IDS) {
        db.execSQL("CREATE TABLE payments(_id INTEGER PRIMARY KEY, " +
                   "uuid TEXT DEFAULT NULL, " +
                   "recipient INTEGER DEFAULT 0, " +
                   "recipient_address TEXT DEFAULT NULL, " +
                   "timestamp INTEGER, " +
                   "note TEXT DEFAULT NULL, " +
                   "direction INTEGER, " +
                   "state INTEGER, " +
                   "failure_reason INTEGER, " +
                   "amount BLOB NOT NULL, " +
                   "fee BLOB NOT NULL, " +
                   "transaction_record BLOB DEFAULT NULL, " +
                   "receipt BLOB DEFAULT NULL, " +
                   "payment_metadata BLOB DEFAULT NULL, " +
                   "receipt_public_key TEXT DEFAULT NULL, " +
                   "block_index INTEGER DEFAULT 0, " +
                   "block_timestamp INTEGER DEFAULT 0, " +
                   "seen INTEGER, " +
                   "UNIQUE(uuid) ON CONFLICT ABORT)");

        db.execSQL("CREATE INDEX IF NOT EXISTS timestamp_direction_index ON payments (timestamp, direction);");
        db.execSQL("CREATE INDEX IF NOT EXISTS timestamp_index ON payments (timestamp);");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS receipt_public_key_index ON payments (receipt_public_key);");

        ///

        ContentValues values = new ContentValues();
        values.putNull("storage_service_key");
        int count = db.update("recipient", values, "storage_service_key NOT NULL AND ((phone NOT NULL AND INSTR(phone, '+') = 0) OR (group_id NOT NULL AND (LENGTH(group_id) != 85 and LENGTH(group_id) != 53)))", null);
        Log.i(TAG, "There were " + count + " bad rows that had their storageID removed.");
      }

      if (oldVersion < MP4_GIF_SUPPORT_AND_BLUR_AVATARS_AND_CLEAN_STORAGE_IDS_WITHOUT_INFO) {
        db.execSQL("ALTER TABLE part ADD COLUMN video_gif INTEGER DEFAULT 0");

        ///

        db.execSQL("ALTER TABLE recipient ADD COLUMN extras BLOB DEFAULT NULL");
        db.execSQL("ALTER TABLE recipient ADD COLUMN groups_in_common INTEGER DEFAULT 0");

        String secureOutgoingSms = "EXISTS(SELECT 1 FROM sms WHERE thread_id = t._id AND (type & 31) = 23 AND (type & 10485760) AND (type & 131072 = 0))";
        String secureOutgoingMms = "EXISTS(SELECT 1 FROM mms WHERE thread_id = t._id AND (msg_box & 31) = 23 AND (msg_box & 10485760) AND (msg_box & 131072 = 0))";

        String selectIdsToUpdateProfileSharing = "SELECT r._id FROM recipient AS r INNER JOIN thread AS t ON r._id = t.recipient_ids WHERE profile_sharing = 0 AND (" + secureOutgoingSms + " OR " + secureOutgoingMms + ")";

        db.execSQL("UPDATE recipient SET profile_sharing = 1 WHERE _id IN (" + selectIdsToUpdateProfileSharing + ")");

        String selectIdsWithGroupsInCommon = "SELECT r._id FROM recipient AS r WHERE EXISTS("
                                             + "SELECT 1 FROM groups AS g INNER JOIN recipient AS gr ON (g.recipient_id = gr._id AND gr.profile_sharing = 1) WHERE g.active = 1 AND (g.members LIKE r._id || ',%' OR g.members LIKE '%,' || r._id || ',%' OR g.members LIKE '%,' || r._id)"
                                             + ")";
        db.execSQL("UPDATE recipient SET groups_in_common = 1 WHERE _id IN (" + selectIdsWithGroupsInCommon + ")");

        ///

        ContentValues values = new ContentValues();
        values.putNull("storage_service_key");
        int count = db.update("recipient", values, "storage_service_key NOT NULL AND phone IS NULL AND uuid IS NULL AND group_id IS NULL", null);
        Log.i(TAG, "There were " + count + " bad rows that had their storageID removed due to not having any other identifier.");
      }

      if (oldVersion < CLEAN_REACTION_NOTIFICATIONS) {
        ContentValues values = new ContentValues(1);
        values.put("notified", 1);

        int count = 0;
        count += db.update("sms", values, "notified = 0 AND read = 1 AND reactions_unread = 1 AND NOT ((type & 31) = 23 AND (type & 10485760) AND (type & 131072 = 0))", null);
        count += db.update("mms", values, "notified = 0 AND read = 1 AND reactions_unread = 1 AND NOT ((msg_box & 31) = 23 AND (msg_box & 10485760) AND (msg_box & 131072 = 0))", null);
        Log.d(TAG, "Resetting notified for " + count + " read incoming messages that were incorrectly flipped when receiving reactions");

        List<Long> smsIds = new ArrayList<>();

        try (Cursor cursor = db.query("sms", new String[] { "_id", "reactions", "notified_timestamp" }, "notified = 0 AND reactions_unread = 1", null, null, null, null)) {
          while (cursor.moveToNext()) {
            byte[] reactions         = cursor.getBlob(cursor.getColumnIndexOrThrow("reactions"));
            long   notifiedTimestamp = cursor.getLong(cursor.getColumnIndexOrThrow("notified_timestamp"));

            if (reactions == null) {
              continue;
            }

            try {
              boolean hasReceiveLaterThanNotified = ReactionList.parseFrom(reactions)
                                                                .getReactionsList()
                                                                .stream()
                                                                .anyMatch(r -> r.getReceivedTime() > notifiedTimestamp);
              if (!hasReceiveLaterThanNotified) {
                smsIds.add(cursor.getLong(cursor.getColumnIndexOrThrow("_id")));
              }
            } catch (InvalidProtocolBufferException e) {
              Log.e(TAG, e);
            }
          }
        }

        if (smsIds.size() > 0) {
          Log.d(TAG, "Updating " + smsIds.size() + " records in sms");
          db.execSQL("UPDATE sms SET reactions_last_seen = notified_timestamp WHERE _id in (" + Util.join(smsIds, ",") + ")");
        }

        List<Long> mmsIds = new ArrayList<>();

        try (Cursor cursor = db.query("mms", new String[] { "_id", "reactions", "notified_timestamp" }, "notified = 0 AND reactions_unread = 1", null, null, null, null)) {
          while (cursor.moveToNext()) {
            byte[] reactions         = cursor.getBlob(cursor.getColumnIndexOrThrow("reactions"));
            long   notifiedTimestamp = cursor.getLong(cursor.getColumnIndexOrThrow("notified_timestamp"));

            if (reactions == null) {
              continue;
            }

            try {
              boolean hasReceiveLaterThanNotified = ReactionList.parseFrom(reactions)
                                                                .getReactionsList()
                                                                .stream()
                                                                .anyMatch(r -> r.getReceivedTime() > notifiedTimestamp);
              if (!hasReceiveLaterThanNotified) {
                mmsIds.add(cursor.getLong(cursor.getColumnIndexOrThrow("_id")));
              }
            } catch (InvalidProtocolBufferException e) {
              Log.e(TAG, e);
            }
          }
        }

        if (mmsIds.size() > 0) {
          Log.d(TAG, "Updating " + mmsIds.size() + " records in mms");
          db.execSQL("UPDATE mms SET reactions_last_seen = notified_timestamp WHERE _id in (" + Util.join(mmsIds, ",") + ")");
        }
      }

      if (oldVersion < STORAGE_SERVICE_REFACTOR) {
        int deleteCount;
        int insertCount;
        int updateCount;
        int dirtyCount;

        ContentValues deleteValues = new ContentValues();
        deleteValues.putNull("storage_service_key");
        deleteCount = db.update("recipient", deleteValues, "storage_service_key NOT NULL AND (dirty = 3 OR group_type = 1 OR (group_type = 0 AND registered = 2))", null);

        try (Cursor cursor = db.query("recipient", new String[] { "_id" }, "storage_service_key IS NULL AND (dirty = 2 OR registered = 1)", null, null, null, null)) {
          insertCount = cursor.getCount();

          while (cursor.moveToNext()) {
            ContentValues insertValues = new ContentValues();
            insertValues.put("storage_service_key", Base64.encodeBytes(StorageSyncHelper.generateKey()));

            long id = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
            db.update("recipient", insertValues, "_id = ?", SqlUtil.buildArgs(id));
          }
        }

        try (Cursor cursor = db.query("recipient", new String[] { "_id" }, "storage_service_key NOT NULL AND dirty = 1", null, null, null, null)) {
          updateCount = cursor.getCount();

          while (cursor.moveToNext()) {
            ContentValues updateValues = new ContentValues();
            updateValues.put("storage_service_key", Base64.encodeBytes(StorageSyncHelper.generateKey()));

            long id = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
            db.update("recipient", updateValues, "_id = ?", SqlUtil.buildArgs(id));
          }
        }

        ContentValues clearDirtyValues = new ContentValues();
        clearDirtyValues.put("dirty", 0);
        dirtyCount = db.update("recipient", clearDirtyValues, "dirty != 0", null);

        Log.d(TAG, String.format(Locale.US, "For storage service refactor migration, there were %d inserts, %d updated, and %d deletes. Cleared the dirty status on %d rows.", insertCount, updateCount, deleteCount, dirtyCount));
      }

      if (oldVersion < CLEAR_MMS_STORAGE_IDS) {
        ContentValues deleteValues = new ContentValues();
        deleteValues.putNull("storage_service_key");

        int deleteCount = db.update("recipient", deleteValues, "storage_service_key NOT NULL AND (group_type = 1 OR (group_type = 0 AND phone IS NULL AND uuid IS NULL))", null);

        Log.d(TAG, "Cleared storageIds from " + deleteCount + " rows. They were either MMS groups or empty contacts.");
      }

      if (oldVersion < SERVER_GUID) {
        db.execSQL("ALTER TABLE sms ADD COLUMN server_guid TEXT DEFAULT NULL");
        db.execSQL("ALTER TABLE mms ADD COLUMN server_guid TEXT DEFAULT NULL");
      }

      if (oldVersion < CHAT_COLORS_AND_AVATAR_COLORS_AND_EMOJI_SEARCH) {
        db.execSQL("ALTER TABLE recipient ADD COLUMN chat_colors BLOB DEFAULT NULL");
        db.execSQL("ALTER TABLE recipient ADD COLUMN custom_chat_colors_id INTEGER DEFAULT 0");
        db.execSQL("CREATE TABLE chat_colors (" +
                   "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                   "chat_colors BLOB)");

        Set<Map.Entry<MaterialColor, ChatColors>> entrySet = ChatColorsMapper.getEntrySet();
        String                                    where    = "color = ? AND group_id is NULL";

        for (Map.Entry<MaterialColor, ChatColors> entry : entrySet) {
          String[]      whereArgs = SqlUtil.buildArgs(entry.getKey().serialize());
          ContentValues values    = new ContentValues(2);

          values.put("chat_colors", entry.getValue().serialize().toByteArray());
          values.put("custom_chat_colors_id", entry.getValue().getId().getLongValue());

          db.update("recipient", values, where, whereArgs);
        }

        ///

        try (Cursor cursor = db.query("recipient", new String[] { "_id" }, "color IS NULL", null, null, null, null)) {
          while (cursor.moveToNext()) {
            long id = cursor.getInt(cursor.getColumnIndexOrThrow("_id"));

            ContentValues values = new ContentValues(1);
            values.put("color", AvatarColor.random().serialize());

            db.update("recipient", values, "_id = ?", new String[] { String.valueOf(id) });
          }
        }

        ///

        db.execSQL("CREATE VIRTUAL TABLE emoji_search USING fts5(label, emoji UNINDEXED)");
      }

      if (oldVersion < SENDER_KEY && !SqlUtil.tableExists(db, "sender_keys")) {
        db.execSQL("CREATE TABLE sender_keys (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                   "recipient_id INTEGER NOT NULL, " +
                   "device INTEGER NOT NULL, " +
                   "distribution_id TEXT NOT NULL, " +
                   "record BLOB NOT NULL, " +
                   "created_at INTEGER NOT NULL, " +
                   "UNIQUE(recipient_id, device, distribution_id) ON CONFLICT REPLACE)");

        db.execSQL("CREATE TABLE sender_key_shared (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                   "distribution_id TEXT NOT NULL, " +
                   "address TEXT NOT NULL, " +
                   "device INTEGER NOT NULL, " +
                   "UNIQUE(distribution_id, address, device) ON CONFLICT REPLACE)");

        db.execSQL("CREATE TABLE pending_retry_receipts (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                   "author TEXT NOT NULL, " +
                   "device INTEGER NOT NULL, " +
                   "sent_timestamp INTEGER NOT NULL, " +
                   "received_timestamp TEXT NOT NULL, " +
                   "thread_id INTEGER NOT NULL, " +
                   "UNIQUE(author, sent_timestamp) ON CONFLICT REPLACE);");

        db.execSQL("ALTER TABLE groups ADD COLUMN distribution_id TEXT DEFAULT NULL");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS group_distribution_id_index ON groups (distribution_id)");

        try (Cursor cursor = db.query("groups", new String[] { "group_id" }, "LENGTH(group_id) = 85", null, null, null, null)) {
          while (cursor.moveToNext()) {
            String        groupId = cursor.getString(cursor.getColumnIndexOrThrow("group_id"));
            ContentValues values  = new ContentValues();

            values.put("distribution_id", DistributionId.create().toString());

            db.update("groups", values, "group_id = ?", new String[] { groupId });
          }
        }
      }

      if (oldVersion < MESSAGE_DUPE_INDEX_AND_MESSAGE_LOG) {
        db.execSQL("DROP INDEX sms_date_sent_index");
        db.execSQL("CREATE INDEX sms_date_sent_index on sms(date_sent, address, thread_id)");

        db.execSQL("DROP INDEX mms_date_sent_index");
        db.execSQL("CREATE INDEX mms_date_sent_index on mms(date, address, thread_id)");

        ///

        db.execSQL("CREATE TABLE message_send_log (_id INTEGER PRIMARY KEY, " +
                   "date_sent INTEGER NOT NULL, " +
                   "content BLOB NOT NULL, " +
                   "related_message_id INTEGER DEFAULT -1, " +
                   "is_related_message_mms INTEGER DEFAULT 0, " +
                   "content_hint INTEGER NOT NULL, " +
                   "group_id BLOB DEFAULT NULL)");

        db.execSQL("CREATE INDEX message_log_date_sent_index ON message_send_log (date_sent)");
        db.execSQL("CREATE INDEX message_log_related_message_index ON message_send_log (related_message_id, is_related_message_mms)");

        db.execSQL("CREATE TRIGGER msl_sms_delete AFTER DELETE ON sms BEGIN DELETE FROM message_send_log WHERE related_message_id = old._id AND is_related_message_mms = 0; END");
        db.execSQL("CREATE TRIGGER msl_mms_delete AFTER DELETE ON mms BEGIN DELETE FROM message_send_log WHERE related_message_id = old._id AND is_related_message_mms = 1; END");

        db.execSQL("CREATE TABLE message_send_log_recipients (_id INTEGER PRIMARY KEY, " +
                   "message_send_log_id INTEGER NOT NULL REFERENCES message_send_log (_id) ON DELETE CASCADE, " +
                   "recipient_id INTEGER NOT NULL, " +
                   "device INTEGER NOT NULL)");

        db.execSQL("CREATE INDEX message_send_log_recipients_recipient_index ON message_send_log_recipients (recipient_id, device)");
      }

      if (oldVersion < MESSAGE_LOG_2) {
        db.execSQL("DROP TABLE message_send_log");
        db.execSQL("DROP INDEX IF EXISTS message_log_date_sent_index");
        db.execSQL("DROP INDEX IF EXISTS message_log_related_message_index");
        db.execSQL("DROP TRIGGER msl_sms_delete");
        db.execSQL("DROP TRIGGER msl_mms_delete");
        db.execSQL("DROP TABLE message_send_log_recipients");
        db.execSQL("DROP INDEX IF EXISTS message_send_log_recipients_recipient_index");

        db.execSQL("CREATE TABLE msl_payload (_id INTEGER PRIMARY KEY, " +
                   "date_sent INTEGER NOT NULL, " +
                   "content BLOB NOT NULL, " +
                   "content_hint INTEGER NOT NULL)");

        db.execSQL("CREATE INDEX msl_payload_date_sent_index ON msl_payload (date_sent)");

        db.execSQL("CREATE TABLE msl_recipient (_id INTEGER PRIMARY KEY, " +
                   "payload_id INTEGER NOT NULL REFERENCES msl_payload (_id) ON DELETE CASCADE, " +
                   "recipient_id INTEGER NOT NULL, " +
                   "device INTEGER NOT NULL)");

        db.execSQL("CREATE INDEX msl_recipient_recipient_index ON msl_recipient (recipient_id, device, payload_id)");
        db.execSQL("CREATE INDEX msl_recipient_payload_index ON msl_recipient (payload_id)");

        db.execSQL("CREATE TABLE msl_message (_id INTEGER PRIMARY KEY, " +
                   "payload_id INTEGER NOT NULL REFERENCES msl_payload (_id) ON DELETE CASCADE, " +
                   "message_id INTEGER NOT NULL, " +
                   "is_mms INTEGER NOT NULL)");

        db.execSQL("CREATE INDEX msl_message_message_index ON msl_message (message_id, is_mms, payload_id)");

        db.execSQL("CREATE TRIGGER msl_sms_delete AFTER DELETE ON sms BEGIN DELETE FROM msl_payload WHERE _id IN (SELECT payload_id FROM msl_message WHERE message_id = old._id AND is_mms = 0); END");
        db.execSQL("CREATE TRIGGER msl_mms_delete AFTER DELETE ON mms BEGIN DELETE FROM msl_payload WHERE _id IN (SELECT payload_id FROM msl_message WHERE message_id = old._id AND is_mms = 1); END");
        db.execSQL("CREATE TRIGGER msl_attachment_delete AFTER DELETE ON part BEGIN DELETE FROM msl_payload WHERE _id IN (SELECT payload_id FROM msl_message WHERE message_id = old.mid AND is_mms = 1); END");
      }

      if (oldVersion < ABANDONED_MESSAGE_CLEANUP) {
        long start          = System.currentTimeMillis();
        int  smsDeleteCount = db.delete("sms", "thread_id NOT IN (SELECT _id FROM thread)", null);
        int  mmsDeleteCount = db.delete("mms", "thread_id NOT IN (SELECT _id FROM thread)", null);
        Log.i(TAG, "Deleted " + smsDeleteCount + " sms and " + mmsDeleteCount + " mms in " + (System.currentTimeMillis() - start) + " ms");
      }

      if (oldVersion < THREAD_AUTOINCREMENT_AND_MMS_AUTOINCREMENT_AND_ABANDONED_ATTACHMENT_CLEANUP) {
        Stopwatch stopwatch = new Stopwatch("thread-autoincrement");

        db.execSQL("CREATE TABLE thread_tmp (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                   "date INTEGER DEFAULT 0, " +
                   "thread_recipient_id INTEGER, " +
                   "message_count INTEGER DEFAULT 0, " +
                   "snippet TEXT, " +
                   "snippet_charset INTEGER DEFAULT 0, " +
                   "snippet_type INTEGER DEFAULT 0, " +
                   "snippet_uri TEXT DEFAULT NULL, " +
                   "snippet_content_type INTEGER DEFAULT NULL, " +
                   "snippet_extras TEXT DEFAULT NULL, " +
                   "read INTEGER DEFAULT 1, " +
                   "type INTEGER DEFAULT 0, " +
                   "error INTEGER DEFAULT 0, " +
                   "archived INTEGER DEFAULT 0, " +
                   "status INTEGER DEFAULT 0, " +
                   "expires_in INTEGER DEFAULT 0, " +
                   "last_seen INTEGER DEFAULT 0, " +
                   "has_sent INTEGER DEFAULT 0, " +
                   "delivery_receipt_count INTEGER DEFAULT 0, " +
                   "read_receipt_count INTEGER DEFAULT 0, " +
                   "unread_count INTEGER DEFAULT 0, " +
                   "last_scrolled INTEGER DEFAULT 0, " +
                   "pinned INTEGER DEFAULT 0)");
        stopwatch.split("table-create");

        db.execSQL("INSERT INTO thread_tmp SELECT _id, " +
                   "date, " +
                   "recipient_ids, " +
                   "message_count, " +
                   "snippet, " +
                   "snippet_cs, " +
                   "snippet_type, " +
                   "snippet_uri, " +
                   "snippet_content_type, " +
                   "snippet_extras, " +
                   "read, " +
                   "type, " +
                   "error, " +
                   "archived, " +
                   "status, " +
                   "expires_in, " +
                   "last_seen, " +
                   "has_sent, " +
                   "delivery_receipt_count, " +
                   "read_receipt_count, " +
                   "unread_count, " +
                   "last_scrolled, " +
                   "pinned " +
                   "FROM thread");

        stopwatch.split("table-copy");

        db.execSQL("DROP TABLE thread");
        db.execSQL("ALTER TABLE thread_tmp RENAME TO thread");

        stopwatch.split("table-rename");

        db.execSQL("CREATE INDEX thread_recipient_id_index ON thread (thread_recipient_id)");
        db.execSQL("CREATE INDEX archived_count_index ON thread (archived, message_count)");
        db.execSQL("CREATE INDEX thread_pinned_index ON thread (pinned)");

        stopwatch.split("indexes");

        db.execSQL("DELETE FROM remapped_threads");

        stopwatch.split("delete-remap");

        stopwatch.stop(TAG);

        ///

        Stopwatch mmsStopwatch = new Stopwatch("mms-autoincrement");

        db.execSQL("CREATE TABLE mms_tmp (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                   "thread_id INTEGER, " +
                   "date INTEGER, " +
                   "date_received INTEGER, " +
                   "date_server INTEGER DEFAULT -1, " +
                   "msg_box INTEGER, " +
                   "read INTEGER DEFAULT 0, " +
                   "body TEXT, " +
                   "part_count INTEGER, " +
                   "ct_l TEXT, " +
                   "address INTEGER, " +
                   "address_device_id INTEGER, " +
                   "exp INTEGER, " +
                   "m_type INTEGER, " +
                   "m_size INTEGER, " +
                   "st INTEGER, " +
                   "tr_id TEXT, " +
                   "delivery_receipt_count INTEGER DEFAULT 0, " +
                   "mismatched_identities TEXT DEFAULT NULL, " +
                   "network_failures TEXT DEFAULT NULL, " +
                   "subscription_id INTEGER DEFAULT -1, " +
                   "expires_in INTEGER DEFAULT 0, " +
                   "expire_started INTEGER DEFAULT 0, " +
                   "notified INTEGER DEFAULT 0, " +
                   "read_receipt_count INTEGER DEFAULT 0, " +
                   "quote_id INTEGER DEFAULT 0, " +
                   "quote_author TEXT, " +
                   "quote_body TEXT, " +
                   "quote_attachment INTEGER DEFAULT -1, " +
                   "quote_missing INTEGER DEFAULT 0, " +
                   "quote_mentions BLOB DEFAULT NULL, " +
                   "shared_contacts TEXT, " +
                   "unidentified INTEGER DEFAULT 0, " +
                   "previews TEXT, " +
                   "reveal_duration INTEGER DEFAULT 0, " +
                   "reactions BLOB DEFAULT NULL, " +
                   "reactions_unread INTEGER DEFAULT 0, " +
                   "reactions_last_seen INTEGER DEFAULT -1, " +
                   "remote_deleted INTEGER DEFAULT 0, " +
                   "mentions_self INTEGER DEFAULT 0, " +
                   "notified_timestamp INTEGER DEFAULT 0, " +
                   "viewed_receipt_count INTEGER DEFAULT 0, " +
                   "server_guid TEXT DEFAULT NULL);");

        mmsStopwatch.split("table-create");

        db.execSQL("INSERT INTO mms_tmp SELECT _id, " +
                   "thread_id, " +
                   "date, " +
                   "date_received, " +
                   "date_server, " +
                   "msg_box, " +
                   "read, " +
                   "body, " +
                   "part_count, " +
                   "ct_l, " +
                   "address, " +
                   "address_device_id, " +
                   "exp, " +
                   "m_type, " +
                   "m_size, " +
                   "st, " +
                   "tr_id, " +
                   "delivery_receipt_count, " +
                   "mismatched_identities, " +
                   "network_failures, " +
                   "subscription_id, " +
                   "expires_in, " +
                   "expire_started, " +
                   "notified, " +
                   "read_receipt_count, " +
                   "quote_id, " +
                   "quote_author, " +
                   "quote_body, " +
                   "quote_attachment, " +
                   "quote_missing, " +
                   "quote_mentions, " +
                   "shared_contacts, " +
                   "unidentified, " +
                   "previews, " +
                   "reveal_duration, " +
                   "reactions, " +
                   "reactions_unread, " +
                   "reactions_last_seen, " +
                   "remote_deleted, " +
                   "mentions_self, " +
                   "notified_timestamp, " +
                   "viewed_receipt_count, " +
                   "server_guid " +
                   "FROM mms");

        mmsStopwatch.split("table-copy");

        db.execSQL("DROP TABLE mms");
        db.execSQL("ALTER TABLE mms_tmp RENAME TO mms");

        mmsStopwatch.split("table-rename");

        db.execSQL("CREATE INDEX mms_read_and_notified_and_thread_id_index ON mms(read, notified, thread_id)");
        db.execSQL("CREATE INDEX mms_message_box_index ON mms (msg_box)");
        db.execSQL("CREATE INDEX mms_date_sent_index ON mms (date, address, thread_id)");
        db.execSQL("CREATE INDEX mms_date_server_index ON mms (date_server)");
        db.execSQL("CREATE INDEX mms_thread_date_index ON mms (thread_id, date_received)");
        db.execSQL("CREATE INDEX mms_reactions_unread_index ON mms (reactions_unread)");

        mmsStopwatch.split("indexes");

        db.execSQL("CREATE TRIGGER mms_ai AFTER INSERT ON mms BEGIN INSERT INTO mms_fts(rowid, body, thread_id) VALUES (new._id, new.body, new.thread_id); END");
        db.execSQL("CREATE TRIGGER mms_ad AFTER DELETE ON mms BEGIN INSERT INTO mms_fts(mms_fts, rowid, body, thread_id) VALUES('delete', old._id, old.body, old.thread_id); END");
        db.execSQL("CREATE TRIGGER mms_au AFTER UPDATE ON mms BEGIN INSERT INTO mms_fts(mms_fts, rowid, body, thread_id) VALUES('delete', old._id, old.body, old.thread_id); INSERT INTO mms_fts(rowid, body, thread_id) VALUES (new._id, new.body, new.thread_id); END");
        db.execSQL("CREATE TRIGGER msl_mms_delete AFTER DELETE ON mms BEGIN DELETE FROM msl_payload WHERE _id IN (SELECT payload_id FROM msl_message WHERE message_id = old._id AND is_mms = 1); END");

        mmsStopwatch.split("triggers");
        mmsStopwatch.stop(TAG);

        Stopwatch smsStopwatch = new Stopwatch("sms-autoincrement");

        db.execSQL("CREATE TABLE sms_tmp (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                   "thread_id INTEGER, " +
                   "address INTEGER, " +
                   "address_device_id INTEGER DEFAULT 1, " +
                   "person INTEGER, " +
                   "date INTEGER, " +
                   "date_sent INTEGER, " +
                   "date_server INTEGER DEFAULT -1, " +
                   "protocol INTEGER, " +
                   "read INTEGER DEFAULT 0, " +
                   "status INTEGER DEFAULT -1, " +
                   "type INTEGER, " +
                   "reply_path_present INTEGER, " +
                   "delivery_receipt_count INTEGER DEFAULT 0, " +
                   "subject TEXT, " +
                   "body TEXT, " +
                   "mismatched_identities TEXT DEFAULT NULL, " +
                   "service_center TEXT, " +
                   "subscription_id INTEGER DEFAULT -1, " +
                   "expires_in INTEGER DEFAULT 0, " +
                   "expire_started INTEGER DEFAULT 0, " +
                   "notified DEFAULT 0, " +
                   "read_receipt_count INTEGER DEFAULT 0, " +
                   "unidentified INTEGER DEFAULT 0, " +
                   "reactions BLOB DEFAULT NULL, " +
                   "reactions_unread INTEGER DEFAULT 0, " +
                   "reactions_last_seen INTEGER DEFAULT -1, " +
                   "remote_deleted INTEGER DEFAULT 0, " +
                   "notified_timestamp INTEGER DEFAULT 0, " +
                   "server_guid TEXT DEFAULT NULL)");

        smsStopwatch.split("table-create");

        db.execSQL("INSERT INTO sms_tmp SELECT _id, " +
                   "thread_id, " +
                   "address, " +
                   "address_device_id, " +
                   "person, " +
                   "date, " +
                   "date_sent, " +
                   "date_server , " +
                   "protocol, " +
                   "read, " +
                   "status , " +
                   "type, " +
                   "reply_path_present, " +
                   "delivery_receipt_count, " +
                   "subject, " +
                   "body, " +
                   "mismatched_identities, " +
                   "service_center, " +
                   "subscription_id , " +
                   "expires_in, " +
                   "expire_started, " +
                   "notified, " +
                   "read_receipt_count, " +
                   "unidentified, " +
                   "reactions BLOB, " +
                   "reactions_unread, " +
                   "reactions_last_seen , " +
                   "remote_deleted, " +
                   "notified_timestamp, " +
                   "server_guid " +
                   "FROM sms");

        smsStopwatch.split("table-copy");

        db.execSQL("DROP TABLE sms");
        db.execSQL("ALTER TABLE sms_tmp RENAME TO sms");

        smsStopwatch.split("table-rename");

        db.execSQL("CREATE INDEX sms_read_and_notified_and_thread_id_index ON sms(read, notified, thread_id)");
        db.execSQL("CREATE INDEX sms_type_index ON sms (type)");
        db.execSQL("CREATE INDEX sms_date_sent_index ON sms (date_sent, address, thread_id)");
        db.execSQL("CREATE INDEX sms_date_server_index ON sms (date_server)");
        db.execSQL("CREATE INDEX sms_thread_date_index ON sms (thread_id, date)");
        db.execSQL("CREATE INDEX sms_reactions_unread_index ON sms (reactions_unread)");

        smsStopwatch.split("indexes");

        db.execSQL("CREATE TRIGGER sms_ai AFTER INSERT ON sms BEGIN INSERT INTO sms_fts(rowid, body, thread_id) VALUES (new._id, new.body, new.thread_id); END;");
        db.execSQL("CREATE TRIGGER sms_ad AFTER DELETE ON sms BEGIN INSERT INTO sms_fts(sms_fts, rowid, body, thread_id) VALUES('delete', old._id, old.body, old.thread_id); END;");
        db.execSQL("CREATE TRIGGER sms_au AFTER UPDATE ON sms BEGIN INSERT INTO sms_fts(sms_fts, rowid, body, thread_id) VALUES('delete', old._id, old.body, old.thread_id); INSERT INTO sms_fts(rowid, body, thread_id) VALUES(new._id, new.body, new.thread_id); END;");
        db.execSQL("CREATE TRIGGER msl_sms_delete AFTER DELETE ON sms BEGIN DELETE FROM msl_payload WHERE _id IN (SELECT payload_id FROM msl_message WHERE message_id = old._id AND is_mms = 0); END");

        smsStopwatch.split("triggers");
        smsStopwatch.stop(TAG);

        ///

        db.delete("part", "mid != -8675309 AND mid NOT IN (SELECT _id FROM mms)", null);
      }

      if (oldVersion < AVATAR_PICKER) {
        db.execSQL("CREATE TABLE avatar_picker (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                   "last_used INTEGER DEFAULT 0, " +
                   "group_id TEXT DEFAULT NULL, " +
                   "avatar BLOB NOT NULL)");

        try (Cursor cursor = db.query("recipient", new String[] { "_id" }, "color IS NULL", null, null, null, null)) {
          while (cursor.moveToNext()) {
            long id = cursor.getInt(cursor.getColumnIndexOrThrow("_id"));

            ContentValues values = new ContentValues(1);
            values.put("color", AvatarColor.random().serialize());

            db.update("recipient", values, "_id = ?", new String[] { String.valueOf(id) });
          }
        }
      }

      if (oldVersion < THREAD_CLEANUP) {
        db.delete("mms", "thread_id NOT IN (SELECT _id FROM thread)", null);
        db.delete("part", "mid != -8675309 AND mid NOT IN (SELECT _id FROM mms)", null);
      }

      if (oldVersion < SESSION_AND_IDENTITY_MIGRATION_AND_GROUP_CALL_RING_TABLE_AND_CLEANUP_SESSION_MIGRATION) {
        long startSessionMigration = System.currentTimeMillis();

        db.execSQL("CREATE TABLE sessions_tmp (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                   "address TEXT NOT NULL, " +
                   "device INTEGER NOT NULL, " +
                   "record BLOB NOT NULL, " +
                   "UNIQUE(address, device))");

        db.execSQL("INSERT INTO sessions_tmp (address, device, record) " +
                   "SELECT COALESCE(recipient.uuid, recipient.phone) AS new_address, " +
                   "sessions.device, " +
                   "sessions.record " +
                   "FROM sessions INNER JOIN recipient ON sessions.address = recipient._id " +
                   "WHERE new_address NOT NULL");

        db.execSQL("DROP TABLE sessions");
        db.execSQL("ALTER TABLE sessions_tmp RENAME TO sessions");

        Log.d(TAG, "Session migration took " + (System.currentTimeMillis() - startSessionMigration) + " ms");

        ///

        long startIdentityMigration = System.currentTimeMillis();

        db.execSQL("CREATE TABLE identities_tmp (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                   "address TEXT UNIQUE NOT NULL, " +
                   "identity_key TEXT, " +
                   "first_use INTEGER DEFAULT 0, " +
                   "timestamp INTEGER DEFAULT 0, " +
                   "verified INTEGER DEFAULT 0, " +
                   "nonblocking_approval INTEGER DEFAULT 0)");

        db.execSQL("INSERT INTO identities_tmp (address, identity_key, first_use, timestamp, verified, nonblocking_approval) " +
                   "SELECT COALESCE(recipient.uuid, recipient.phone) AS new_address, " +
                   "identities.key, " +
                   "identities.first_use, " +
                   "identities.timestamp, " +
                   "identities.verified, " +
                   "identities.nonblocking_approval " +
                   "FROM identities INNER JOIN recipient ON identities.address = recipient._id " +
                   "WHERE new_address NOT NULL");

        db.execSQL("DROP TABLE identities");
        db.execSQL("ALTER TABLE identities_tmp RENAME TO identities");

        Log.d(TAG, "Identity migration took " + (System.currentTimeMillis() - startIdentityMigration) + " ms");

        ///

        db.execSQL("CREATE TABLE group_call_ring (_id INTEGER PRIMARY KEY, ring_id INTEGER UNIQUE, date_received INTEGER, ring_state INTEGER)");
        db.execSQL("CREATE INDEX date_received_index on group_call_ring (date_received)");

        ///

        int sessionCount = db.delete("sessions", "address LIKE '+%'", null);
        Log.i(TAG, "Cleaned up " + sessionCount + " sessions.");

        ContentValues storageValues = new ContentValues();
        storageValues.putNull("storage_service_key");

        int storageCount = db.update("recipient", storageValues, "storage_service_key NOT NULL AND group_id IS NULL AND uuid IS NULL", null);
        Log.i(TAG, "Cleaned up " + storageCount + " storageIds.");
      }

      if (oldVersion < RECEIPT_TIMESTAMP) {
        db.execSQL("ALTER TABLE sms ADD COLUMN receipt_timestamp INTEGER DEFAULT -1");
        db.execSQL("ALTER TABLE mms ADD COLUMN receipt_timestamp INTEGER DEFAULT -1");
      }

      if (oldVersion < BADGES) {
        db.execSQL("ALTER TABLE recipient ADD COLUMN badges BLOB DEFAULT NULL");
      }

      if (oldVersion < SENDER_KEY_UUID) {
        long start = System.currentTimeMillis();

        db.execSQL("CREATE TABLE sender_keys_tmp (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                   "address TEXT NOT NULL, " +
                   "device INTEGER NOT NULL, " +
                   "distribution_id TEXT NOT NULL, " +
                   "record BLOB NOT NULL, " +
                   "created_at INTEGER NOT NULL, " +
                   "UNIQUE(address, device, distribution_id) ON CONFLICT REPLACE)");

        db.execSQL("INSERT INTO sender_keys_tmp (address, device, distribution_id, record, created_at) "  +
                   "SELECT recipient.uuid AS new_address, " +
                   "sender_keys.device, " +
                   "sender_keys.distribution_id, " +
                   "sender_keys.record, " +
                   "sender_keys.created_at " +
                   "FROM sender_keys INNER JOIN recipient ON sender_keys.recipient_id = recipient._id " +
                   "WHERE new_address NOT NULL");

        db.execSQL("DROP TABLE sender_keys");
        db.execSQL("ALTER TABLE sender_keys_tmp RENAME TO sender_keys");

        Log.d(TAG, "Sender key migration took " + (System.currentTimeMillis() - start) + " ms");
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }

    Log.i(TAG, "Upgrade complete. Took " + (System.currentTimeMillis() - startTime) + " ms.");
  }

  @Override
  public net.zetetic.database.sqlcipher.SQLiteDatabase getReadableDatabase() {
    throw new UnsupportedOperationException("Call getSignalReadableDatabase() instead!");
  }

  @Override
  public net.zetetic.database.sqlcipher.SQLiteDatabase getWritableDatabase() {
    throw new UnsupportedOperationException("Call getSignalReadableDatabase() instead!");
  }

  public net.zetetic.database.sqlcipher.SQLiteDatabase getRawReadableDatabase() {
    return super.getReadableDatabase();
  }

  public net.zetetic.database.sqlcipher.SQLiteDatabase getRawWritableDatabase() {
    return super.getWritableDatabase();
  }

  public su.sres.securesms.database.SQLiteDatabase getSignalReadableDatabase() {
    return new su.sres.securesms.database.SQLiteDatabase(super.getReadableDatabase());
  }

  public su.sres.securesms.database.SQLiteDatabase getSignalWritableDatabase() {
    return new su.sres.securesms.database.SQLiteDatabase(super.getWritableDatabase());
  }

  @Override
  public @NonNull net.zetetic.database.sqlcipher.SQLiteDatabase getSqlCipherDatabase() {
    return super.getWritableDatabase();
  }

  public void markCurrent(SQLiteDatabase db) {
    db.setVersion(DATABASE_VERSION);
  }

  public static boolean databaseFileExists(@NonNull Context context) {
    return context.getDatabasePath(DATABASE_NAME).exists();
  }

  public static File getDatabaseFile(@NonNull Context context) {
    return context.getDatabasePath(DATABASE_NAME);
  }

  private void executeStatements(SQLiteDatabase db, String[] statements) {
    for (String statement : statements)
      db.execSQL(statement);
  }
}
