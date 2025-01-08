package su.sres.securesms.database;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

/**
 * Simple interface for common methods across our various
 * {@link net.zetetic.database.sqlcipher.SQLiteOpenHelper}s.
 */
public interface ShadowDatabaseOpenHelper {
    SQLiteDatabase getSqlCipherDatabase();
    String getDatabaseName();
}
