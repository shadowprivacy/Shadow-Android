package su.sres.signalservice.internal.push.http;

import su.sres.signalservice.api.crypto.DigestingOutputStream;
import su.sres.signalservice.api.crypto.NoCipherOutputStream;

import java.io.OutputStream;

/**
 * See {@link NoCipherOutputStream}.
 */
public final class NoCipherOutputStreamFactory implements OutputStreamFactory {

    @Override
    public DigestingOutputStream createFor(OutputStream wrap) {
        return new NoCipherOutputStream(wrap);
    }
}