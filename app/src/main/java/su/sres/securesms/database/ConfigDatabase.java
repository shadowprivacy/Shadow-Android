package su.sres.securesms.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import net.sqlcipher.database.SQLiteDatabase;

import su.sres.securesms.database.helpers.SQLCipherOpenHelper;
import su.sres.securesms.logging.Log;


public class ConfigDatabase extends Database {

    private static final String TAG = Log.tag(ConfigDatabase.class);

    public static final String TABLE_NAME = "config";
    public static final String _ID = "_id";
    private static final String SHADOW_URL = "shadow_url";

    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + _ID + " INTEGER PRIMARY KEY, " +
            SHADOW_URL + " TEXT NOT NULL)";

    public static final String INITIALIZE_CONFIG = "INSERT INTO " + TABLE_NAME + " VALUES (1, 'https://example.org')";

    public ConfigDatabase(Context context, SQLCipherOpenHelper databaseHelper) {
        super(context, databaseHelper);
    }

    public long setConfigById(String shadowUrl, int configId) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(SHADOW_URL, shadowUrl);

        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        return db.update(TABLE_NAME, contentValues, "_id = ?", new String[] {Integer.toString(configId)});
    }

    public String getConfigById(int configId) {
        SQLiteDatabase db = databaseHelper.getReadableDatabase();

        Cursor cursor = db.query(TABLE_NAME, new String[]{SHADOW_URL}, "_id = ?", new String[] {Integer.toString(configId)}, null, null, null);
        cursor.moveToFirst();

        try {
            if (cursor != null) {
                return cursor.getString(cursor.getColumnIndex(SHADOW_URL));
            }

            else return "https://example.org";

        } finally {
            if (cursor != null) cursor.close();
        }
    }
}
