package su.sres.signalservice.test;

import org.signal.zkgroup.internal.Native;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNoException;

public final class LibSignalLibraryUtil {

    /**
     * Attempts to initialize the LibSignal Native class, which will load the native binaries.
     * <p>
     * If that fails to link, then on Unix, it will fail as we rely on that for CI.
     * <p>
     * If that fails to link, and it's not Unix, it will skip the test via assumption violation.
     * <p>
     * If using inside a PowerMocked test, the assumption violation can be fatal, use:
     * {@code @PowerMockRunnerDelegate(JUnit4.class)}
     */
    public static void assumeLibSignalSupportedOnOS() {
        try {
            Class.forName(Native.class.getName());
        } catch (ClassNotFoundException e) {
            Assert.fail();
        } catch (NoClassDefFoundError | UnsatisfiedLinkError e) {
            String osName = System.getProperty("os.name");

            if (isUnix(osName)) {
                Assert.fail("Not able to link native LibSignal on a key OS: " + osName);
            } else {
                Assume.assumeNoException("Not able to link native LibSignal on this operating system: " + osName, e);
            }
        }
    }

    private static boolean isUnix(String osName) {
        Assert.assertNotNull(osName);
        osName = osName.toLowerCase();
        return osName.contains("nix") || osName.contains("nux") || osName.contains("aix");
    }
}