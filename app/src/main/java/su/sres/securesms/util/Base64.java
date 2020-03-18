package su.sres.securesms.util;

import androidx.annotation.NonNull;

import java.io.IOException;

public final class Base64 {

    private Base64() {
    }

    public static @NonNull byte[] decode(@NonNull String s) throws IOException {
        return su.sres.util.Base64.decode(s);
    }

    public static @NonNull String encodeBytes(@NonNull byte[] source) {
        return su.sres.util.Base64.encodeBytes(source);
    }

    public static @NonNull byte[] decodeOrThrow(@NonNull String s) {
        try {
            return su.sres.util.Base64.decode(s);
        } catch (IOException e) {
            throw new AssertionError();
        }
    }
}