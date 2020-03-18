package su.sres.signalservice.internal.push.http;

import su.sres.zkgroup.profiles.ProfileKey;
import su.sres.signalservice.api.crypto.DigestingOutputStream;
import su.sres.signalservice.api.crypto.ProfileCipherOutputStream;

import java.io.IOException;
import java.io.OutputStream;

public class ProfileCipherOutputStreamFactory implements OutputStreamFactory {

  private final ProfileKey key;

  public ProfileCipherOutputStreamFactory(ProfileKey key) {
    this.key = key;
  }

  @Override
  public DigestingOutputStream createFor(OutputStream wrap) throws IOException {
    return new ProfileCipherOutputStream(wrap, key);
  }

}
