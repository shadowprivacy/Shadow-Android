package su.sres.signalservice.internal.push.http;


import su.sres.signalservice.api.crypto.AttachmentCipherOutputStream;
import su.sres.signalservice.api.crypto.DigestingOutputStream;

import java.io.IOException;
import java.io.OutputStream;

public class AttachmentCipherOutputStreamFactory implements OutputStreamFactory {

  private final byte[] key;

  public AttachmentCipherOutputStreamFactory(byte[] key) {
    this.key = key;
  }

  @Override
  public DigestingOutputStream createFor(OutputStream wrap) throws IOException {
    return new AttachmentCipherOutputStream(key, wrap);
  }

}
