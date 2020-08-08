/**
 *  Adopted from https://github.com/verhas/License3j
 */

package su.sres.securesms.activation;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Reads a license from some input.
 */
public class LicenseReader implements Closeable {

    private final InputStream is;
    private boolean closed = false;
    /**
     * Create a new license reader that will read the license from the input stream. Note that using this version of
     * LicenseReader does not provide any protection against enormously and erroneously large input. The caller has to
     * make sure the source of the input stream is really a license file and that it is not too large for the
     * application with the actual memory settings.
     *
     * @param is the input stream from which the license is to be read
     */
    public LicenseReader(InputStream is) {
        Objects.requireNonNull(is);
        this.is = is;
    }

    /**
     * Read the license from the input assuming the license is binary formatted.
     *
     * @return the license created from the file
     * @throws IOException when the file cannot be read
     */
    public License read() throws IOException {
        final License license = License.Create.from(ByteArrayReader.readInput(is));
        close();
        return license;
    }

    public static License read(byte[] lb) throws IOException {
        return License.Create.from(lb);
    }

    @Override
    public void close() throws IOException {
        if (closed) return;
        closed = true;
        if (is != null) {
            is.close();
        }
    }
}
