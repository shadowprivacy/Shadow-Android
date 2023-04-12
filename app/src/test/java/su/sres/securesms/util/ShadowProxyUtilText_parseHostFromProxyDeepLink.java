package su.sres.securesms.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static junit.framework.TestCase.assertEquals;

@RunWith(Parameterized.class)
public class ShadowProxyUtilText_parseHostFromProxyDeepLink {

    private final String input;
    private final String output;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                { "https://proxy.shadowprivacy.com/#proxy.parker.org",     "proxy.parker.org" },
                { "https://proxy.shadowprivacy.com/#proxy.parker.org:443", "proxy.parker.org" },
                { "sgnl://proxy.shadowprivacy.com/#proxy.parker.org",      "proxy.parker.org" },
                { "sgnl://proxy.shadowprivacy.com/#proxy.parker.org:443",  "proxy.parker.org" },
                { "https://proxy.shadowprivacy.com/",                       null },
                { "https://proxy.shadowprivacy.com/#",                      null },
                { "sgnl://proxy.shadowprivacy.com/",                        null },
                { "sgnl://proxy.shadowprivacy.com/#",                       null },
                { "http://proxy.shadowprivacy.com/#proxy.parker.org",       null },
                { "proxy.shadowprivacy.com/#proxy.parker.org",              null },
                { "",                                           null },
                { null,                                         null }
        });
    }

    public ShadowProxyUtilText_parseHostFromProxyDeepLink(String input, String output) {
        this.input  = input;
        this.output = output;
    }

    @Test
    public void parse() {
        assertEquals(output, ShadowProxyUtil.parseHostFromProxyDeepLink(input));
    }
}
