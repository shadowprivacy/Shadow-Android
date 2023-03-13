package su.sres.securesms.database.helpers;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import su.sres.securesms.database.JobDatabase;
import su.sres.securesms.database.KeyValueDatabase;
import su.sres.securesms.database.MegaphoneDatabase;
import su.sres.securesms.database.MentionDatabase;
import su.sres.securesms.database.SignalDatabase;
import su.sres.securesms.database.SqlCipherDatabaseHook;
import su.sres.securesms.groups.GroupId;
import su.sres.core.util.logging.Log;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteDatabaseHook;
import net.sqlcipher.database.SQLiteOpenHelper;

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
import su.sres.securesms.database.StorageKeyDatabase;
import su.sres.securesms.database.ThreadDatabase;
import su.sres.securesms.util.CursorUtil;
import su.sres.securesms.util.Hex;
import su.sres.securesms.util.SqlUtil;
import su.sres.securesms.util.Triple;
import su.sres.securesms.util.Util;

import java.io.File;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class SQLCipherOpenHelper extends SQLiteOpenHelper implements SignalDatabase {

    @SuppressWarnings("unused")
    private static final String TAG = SQLCipherOpenHelper.class.getSimpleName();

    private static final int SERVER_DELIVERED_TIMESTAMP = 64;
    private static final int QUOTE_CLEANUP = 65;
    private static final int BORDERLESS = 66;
    private static final int MENTIONS = 67;
    private static final int PINNED_CONVERSATIONS_MENTION_GLOBAL_SETTING_MIGRATION_UNKNOWN_STORAGE_FIELDS = 68;
    private static final int STICKER_CONTENT_TYPE_EMOJI_IN_NOTIFICATIONS = 69;
    private static final int THUMBNAIL_CLEANUP_AND_STICKER_CONTENT_TYPE_CLEANUP_AND_MENTION_CLEANUP = 70;
    private static final int REACTION_CLEANUP = 71;
    private static final int CAPABILITIES_REFACTOR_AND_GV1_MIGRATION = 72;
    private static final int NOTIFIED_TIMESTAMP_AND_GV1_MIGRATION_LAST_SEEN = 73;
    private static final int VIEWED_RECEIPTS_CLEAN_UP_GV1_IDS = 74;
    private static final int GV1_MIGRATION_REFACTOR = 75;
    private static final int CLEAR_PROFILE_KEY_CREDENTIALS    = 76;

    private static final int DATABASE_VERSION = 76;
    private static final String DATABASE_NAME = "shadow.db";

    private final Context context;
    private final DatabaseSecret databaseSecret;

    public SQLCipherOpenHelper(@NonNull Context context, @NonNull DatabaseSecret databaseSecret) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION, new SqlCipherDatabaseHook());

        this.context = context.getApplicationContext();
        this.databaseSecret = databaseSecret;
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
        db.execSQL(StickerDatabase.CREATE_TABLE);
        db.execSQL(StorageKeyDatabase.CREATE_TABLE);
        db.execSQL(MentionDatabase.CREATE_TABLE);

        executeStatements(db, SearchDatabase.CREATE_TABLE);

        executeStatements(db, RecipientDatabase.CREATE_INDEXS);
        executeStatements(db, SmsDatabase.CREATE_INDEXS);
        executeStatements(db, MmsDatabase.CREATE_INDEXS);
        executeStatements(db, AttachmentDatabase.CREATE_INDEXS);
        executeStatements(db, ThreadDatabase.CREATE_INDEXS);
        executeStatements(db, DraftDatabase.CREATE_INDEXS);
        executeStatements(db, GroupDatabase.CREATE_INDEXS);
        executeStatements(db, GroupReceiptDatabase.CREATE_INDEXES);
        executeStatements(db, StickerDatabase.CREATE_INDEXES);
        executeStatements(db, StorageKeyDatabase.CREATE_INDEXES);
        executeStatements(db, MentionDatabase.CREATE_INDEXES);
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
                            db.update("part", values, "_data = ?", new String[]{data});

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
                int total = 0;
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
                        int rangeStart = CursorUtil.requireInt(cursor, "range_start");
                        int rangeLength = CursorUtil.requireInt(cursor, "range_length");
                        String body = CursorUtil.requireString(cursor, "body");

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

                List<Long> idsToDelete2 = new LinkedList<>();
                Set<Triple<Long, Integer, Integer>> mentionTuples = new HashSet<>();
                try (Cursor cursor = db.rawQuery("select mention.*, mms.body from mention inner join mms on mention.message_id = mms._id order by mention._id desc", null)) {
                    while (cursor != null && cursor.moveToNext()) {
                        long mentionId = CursorUtil.requireLong(cursor, "_id");
                        long messageId = CursorUtil.requireLong(cursor, "message_id");
                        int rangeStart = CursorUtil.requireInt(cursor, "range_start");
                        int rangeLength = CursorUtil.requireInt(cursor, "range_length");
                        String body = CursorUtil.requireString(cursor, "body");

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
                db.update("sms", values, "remote_deleted = ?", new String[]{"1"});
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
                        "AND (_id NOT IN (SELECT recipient_ids FROM thread) OR _id IN (SELECT recipient_ids FROM thread WHERE message_count = 0))", null)) {
                    while (cursor.moveToNext()) {
                        String recipientId = cursor.getString(cursor.getColumnIndexOrThrow("_id"));
                        String groupIdV1 = cursor.getString(cursor.getColumnIndexOrThrow("group_id"));
                        deletableRecipients.add(recipientId);
                        Log.d(TAG, String.format(Locale.US, "Found invalid GV1 on %s with no or empty thread %s length %d", recipientId, groupIdV1, groupIdV1.length()));
                    }
                }

                for (String recipientId : deletableRecipients) {
                    db.delete("recipient", "_id = ?", new String[]{recipientId});
                    Log.d(TAG, "Deleted recipient " + recipientId);
                }

                List<String> orphanedThreads = new LinkedList<>();
                try (Cursor cursor = db.rawQuery("SELECT _id FROM thread WHERE message_count = 0 AND recipient_ids NOT IN (SELECT _id FROM recipient)", null)) {
                    while (cursor.moveToNext()) {
                        orphanedThreads.add(cursor.getString(cursor.getColumnIndexOrThrow("_id")));
                    }
                }

                for (String orphanedThreadId : orphanedThreads) {
                    db.delete("thread", "_id = ?", new String[]{orphanedThreadId});
                    Log.d(TAG, "Deleted orphaned thread " + orphanedThreadId);
                }

                List<String> remainingInvalidGV1Recipients = new LinkedList<>();
                try (Cursor cursor = db.rawQuery("SELECT _id, group_id FROM recipient\n" +
                        "WHERE group_id NOT IN (SELECT group_id FROM groups)\n" +
                        "AND group_id LIKE '__textsecure_group__!%' AND length(group_id) <> 53\n" +
                        "AND _id IN (SELECT recipient_ids FROM thread)", null)) {
                    while (cursor.moveToNext()) {
                        String recipientId = cursor.getString(cursor.getColumnIndexOrThrow("_id"));
                        String groupIdV1 = cursor.getString(cursor.getColumnIndexOrThrow("group_id"));
                        remainingInvalidGV1Recipients.add(recipientId);
                        Log.d(TAG, String.format(Locale.US, "Found invalid GV1 on %s with non-empty thread %s length %d", recipientId, groupIdV1, groupIdV1.length()));
                    }
                }

                for (String recipientId : remainingInvalidGV1Recipients) {
                    String newId = "__textsecure_group__!" + Hex.toStringCondensed(Util.getSecretBytes(16));
                    ContentValues values = new ContentValues(1);
                    values.put("group_id", newId);

                    db.update("recipient", values, "_id = ?", new String[]{String.valueOf(recipientId)});
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

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        Log.i(TAG, "Upgrade complete. Took " + (System.currentTimeMillis() - startTime) + " ms.");
    }

    public su.sres.securesms.database.SQLiteDatabase getReadableDatabase() {
        return new su.sres.securesms.database.SQLiteDatabase(getReadableDatabase(databaseSecret.asString()));
    }

    public su.sres.securesms.database.SQLiteDatabase getWritableDatabase() {
        return new su.sres.securesms.database.SQLiteDatabase(getWritableDatabase(databaseSecret.asString()));
    }

    @Override
    public @NonNull SQLiteDatabase getSqlCipherDatabase() {
        return getWritableDatabase().getSqlCipherDatabase();
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
