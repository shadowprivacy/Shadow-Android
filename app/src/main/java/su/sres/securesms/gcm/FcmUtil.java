package su.sres.securesms.gcm;

import androidx.annotation.WorkerThread;

import android.text.TextUtils;

import com.google.firebase.iid.FirebaseInstanceId;

import su.sres.securesms.keyvalue.SignalStore;
import su.sres.securesms.logging.Log;

import org.whispersystems.libsignal.util.guava.Optional;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

public final class FcmUtil {

    private static final String TAG = FcmUtil.class.getSimpleName();

    /**
     * Retrieves the current FCM token. If one isn't available, it'll be generated.
     */
    @WorkerThread
    public static Optional<String> getToken() {
        AtomicReference<String> token = new AtomicReference<>(null);

        try {
            String fcmSenderId = SignalStore.serviceConfigurationValues().getFcmSenderId();

            if (fcmSenderId.equals("null")) {
                Log.e(TAG, "FCM sender ID is null");
            } else {
                token.set(FirebaseInstanceId.getInstance().getToken(fcmSenderId, "FCM"));
            }
        } catch (IOException e) {
            Log.w(TAG, "Failed to get the token.", e);
        }

        return Optional.fromNullable(token.get());
    }
}