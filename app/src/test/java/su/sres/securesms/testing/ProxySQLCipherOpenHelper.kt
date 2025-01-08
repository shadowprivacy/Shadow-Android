package su.sres.securesms.testing

import android.app.Application
import android.content.Context
import su.sres.securesms.crypto.AttachmentSecret
import su.sres.securesms.crypto.DatabaseSecret
import su.sres.securesms.database.ShadowDatabase
import java.security.SecureRandom
import android.database.sqlite.SQLiteDatabase as AndroidSQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteDatabase as SQLCipherSQLiteDatabase
import su.sres.securesms.database.SQLiteDatabase as SignalSQLiteDatabase

/**
 * Proxy [ShadowDatabase] to the [TestSQLiteOpenHelper] interface.
 */
class ProxySQLCipherOpenHelper(
  context: Application,
  val readableDatabase: AndroidSQLiteDatabase,
  val writableDatabase: AndroidSQLiteDatabase,
) : ShadowDatabase(context, DatabaseSecret(ByteArray(32).apply { SecureRandom().nextBytes(this) }), AttachmentSecret()) {

  constructor(context: Application, testOpenHelper: TestSQLiteOpenHelper) : this(context, testOpenHelper.readableDatabase, testOpenHelper.writableDatabase)

  override fun close() {
    throw UnsupportedOperationException()
  }

  override fun getDatabaseName(): String {
    throw UnsupportedOperationException()
  }

  override fun setWriteAheadLoggingEnabled(enabled: Boolean) {
    throw UnsupportedOperationException()
  }

  override fun onConfigure(db: SQLCipherSQLiteDatabase) {
    throw UnsupportedOperationException()
  }

  override fun onBeforeDelete(db: SQLCipherSQLiteDatabase?) {
    throw UnsupportedOperationException()
  }

  override fun onDowngrade(db: SQLCipherSQLiteDatabase?, oldVersion: Int, newVersion: Int) {
    throw UnsupportedOperationException()
  }

  override fun onOpen(db: net.zetetic.database.sqlcipher.SQLiteDatabase) {
    throw UnsupportedOperationException()
  }

  override fun onCreate(db: net.zetetic.database.sqlcipher.SQLiteDatabase) {
    throw UnsupportedOperationException()
  }

  override fun onUpgrade(db: net.zetetic.database.sqlcipher.SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    throw UnsupportedOperationException()
  }

  override fun getReadableDatabase(): SQLCipherSQLiteDatabase {
    throw UnsupportedOperationException()
  }

  override fun getWritableDatabase(): SQLCipherSQLiteDatabase {
    throw UnsupportedOperationException()
  }

  override val rawReadableDatabase: net.zetetic.database.sqlcipher.SQLiteDatabase
    get() = throw UnsupportedOperationException()

  override val rawWritableDatabase: net.zetetic.database.sqlcipher.SQLiteDatabase
    get() = throw UnsupportedOperationException()

  override val signalReadableDatabase: su.sres.securesms.database.SQLiteDatabase
    get() = ProxySignalSQLiteDatabase(readableDatabase)

  override val signalWritableDatabase: su.sres.securesms.database.SQLiteDatabase
    get() = ProxySignalSQLiteDatabase(writableDatabase)

  override fun getSqlCipherDatabase(): SQLCipherSQLiteDatabase {
    throw UnsupportedOperationException()
  }

  override fun markCurrent(db: net.zetetic.database.sqlcipher.SQLiteDatabase) {
    throw UnsupportedOperationException()
  }
}