package su.sres.securesms.push;

import android.content.Context;

import su.sres.securesms.keyvalue.SignalStore;
import su.sres.signalservice.api.push.TrustStore;

import java.io.FileNotFoundException;
import java.io.InputStream;

import static su.sres.securesms.InitialActivity.TRUSTSTORE_FILE_NAME;

public class SignalServiceTrustStore implements TrustStore {

  private final Context context;

  public SignalServiceTrustStore(Context context) {
    this.context = context.getApplicationContext();
  }

  @Override
  public InputStream getKeyStoreInputStream() throws FileNotFoundException {
      return context.openFileInput(TRUSTSTORE_FILE_NAME);
  }

  @Override
  public String getKeyStorePassword() {
      return SignalStore.registrationValues().getStorePass();
  }
}
