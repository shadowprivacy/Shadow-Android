package su.sres.securesms.crypto.storage;

import android.content.Context;
import androidx.annotation.NonNull;

import su.sres.core.util.logging.Log;
import su.sres.securesms.database.DatabaseFactory;

import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.PreKeyStore;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyStore;

import java.util.List;

public class TextSecurePreKeyStore implements PreKeyStore, SignedPreKeyStore {

  @SuppressWarnings("unused")
  private static final String TAG = Log.tag(TextSecurePreKeyStore.class);

  private static final Object LOCK = new Object();

  @NonNull
  private final Context context;

  public TextSecurePreKeyStore(@NonNull Context context) {
    this.context = context;
  }

  @Override
  public PreKeyRecord loadPreKey(int preKeyId) throws InvalidKeyIdException {
    synchronized (LOCK) {
      PreKeyRecord preKeyRecord = DatabaseFactory.getPreKeyDatabase(context).getPreKey(preKeyId);

      if (preKeyRecord == null) throw new InvalidKeyIdException("No such key: " + preKeyId);
      else                      return preKeyRecord;
    }
  }

  @Override
  public SignedPreKeyRecord loadSignedPreKey(int signedPreKeyId) throws InvalidKeyIdException {
    synchronized (LOCK) {
      SignedPreKeyRecord signedPreKeyRecord = DatabaseFactory.getSignedPreKeyDatabase(context).getSignedPreKey(signedPreKeyId);

      if (signedPreKeyRecord == null) throw new InvalidKeyIdException("No such signed prekey: " + signedPreKeyId);
      else                            return signedPreKeyRecord;
    }
  }

  @Override
  public List<SignedPreKeyRecord> loadSignedPreKeys() {
    synchronized (LOCK) {
      return DatabaseFactory.getSignedPreKeyDatabase(context).getAllSignedPreKeys();
    }
  }

  @Override
  public void storePreKey(int preKeyId, PreKeyRecord record) {
    synchronized (LOCK) {
      DatabaseFactory.getPreKeyDatabase(context).insertPreKey(preKeyId, record);
    }
  }

  @Override
  public void storeSignedPreKey(int signedPreKeyId, SignedPreKeyRecord record) {
    synchronized (LOCK) {
      DatabaseFactory.getSignedPreKeyDatabase(context).insertSignedPreKey(signedPreKeyId, record);
    }
  }

  @Override
  public boolean containsPreKey(int preKeyId) {
    return DatabaseFactory.getPreKeyDatabase(context).getPreKey(preKeyId) != null;
  }

  @Override
  public boolean containsSignedPreKey(int signedPreKeyId) {
    return DatabaseFactory.getSignedPreKeyDatabase(context).getSignedPreKey(signedPreKeyId) != null;
  }

  @Override
  public void removePreKey(int preKeyId) {
    DatabaseFactory.getPreKeyDatabase(context).removePreKey(preKeyId);
  }

  @Override
  public void removeSignedPreKey(int signedPreKeyId) {
    DatabaseFactory.getSignedPreKeyDatabase(context).removeSignedPreKey(signedPreKeyId);
  }
}
