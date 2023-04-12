package su.sres.securesms.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static junit.framework.TestCase.assertEquals;

@RunWith(Parameterized.class)
public class ShadowProxyUtilText_convertUserEnteredAddressToHost {

    private final String input;
    private final String output;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                { "https://proxy.shadowprivacy.com/#proxy.parker.org",     "proxy.parker.org" },
                { "https://proxy.shadowprivacy.com/#proxy.parker.org:443", "proxy.parker.org" },
                { "sgnl://proxy.shadowprivacy.com/#proxy.parker.org",      "proxy.parker.org" },
                { "sgnl://proxy.shadowprivacy.com/#proxy.parker.org:443",  "proxy.parker.org" },
                { "proxy.parker.org",                          "proxy.parker.org" },
                { "proxy.parker.org:443",                      "proxy.parker.org" },
                { "x",                                         "x" },
                { "",                                          "" }
        });
    }

    public ShadowProxyUtilText_convertUserEnteredAddressToHost(String input, String output) {
        this.input  = input;
        this.output = output;
    }

    @Test
    public void parse() {
        assertEquals(output, ShadowProxyUtil.convertUserEnteredAddressToHost(input));
    }
}
