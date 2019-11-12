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

        long ret = 0;
        try
        {
            ContentValues contentValues = new ContentValues();
            contentValues.put(SHADOW_URL, shadowUrl);

            SQLiteDatabase db = databaseHelper.getWritableDatabase();

            ret = db.update(TABLE_NAME, contentValues, "_id = ?", new String[] {Integer.toString(configId)});;
        }
        catch (Exception e) {
            Log.e(TAG, "Failed to set Config by Id. Exception:" + e);
        }
        return ret;
    }

    public String getConfigById(int configId) {
        SQLiteDatabase db = databaseHelper.getReadableDatabase();
        try {
            Cursor cursor = db.query(TABLE_NAME, new String[]{SHADOW_URL}, "_id = ?", new String[]{Integer.toString(configId)}, null, null, null);
            cursor.moveToFirst();

            if (cursor != null) {
                String url = cursor.getString(cursor.getColumnIndex(SHADOW_URL));
                cursor.close();
                return url;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get Config by Id. Default value was returned. Exception: " + e);
        }

        return "https://example.org";
    }
}
