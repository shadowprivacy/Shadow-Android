package su.sres.signalservice.internal.push.http;


import su.sres.signalservice.api.crypto.DigestingOutputStream;

import java.io.IOException;
import java.io.OutputStream;

public interface OutputStreamFactory {

  public DigestingOutputStream createFor(OutputStream wrap) throws IOException;

}
