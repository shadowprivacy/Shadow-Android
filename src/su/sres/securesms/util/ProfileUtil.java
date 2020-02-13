package su.sres.securesms.util;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import su.sres.securesms.crypto.UnidentifiedAccessUtil;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.logging.Log;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientUtil;
import su.sres.securesms.service.IncomingMessageObserver;
import org.whispersystems.libsignal.util.guava.Optional;
import su.sres.signalservice.api.SignalServiceMessagePipe;
import su.sres.signalservice.api.SignalServiceMessageReceiver;
import su.sres.signalservice.api.crypto.InvalidCiphertextException;
import su.sres.signalservice.api.crypto.ProfileCipher;
import su.sres.signalservice.api.crypto.UnidentifiedAccess;
import su.sres.signalservice.api.crypto.UnidentifiedAccessPair;
import su.sres.signalservice.api.profiles.SignalServiceProfile;
import su.sres.signalservice.api.push.SignalServiceAddress;
import su.sres.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;

import java.io.IOException;

/**
 * Aids in the retrieval and decryption of profiles.
 */
public class ProfileUtil {

    private static final String TAG = Log.tag(ProfileUtil.class);

    @WorkerThread
    public static SignalServiceProfile retrieveProfile(@NonNull Context context, @NonNull Recipient recipient) throws IOException {
        SignalServiceAddress         address            = RecipientUtil.toSignalServiceAddress(context, recipient);
        Optional<UnidentifiedAccess> unidentifiedAccess = getUnidentifiedAccess(context, recipient);

        SignalServiceProfile profile;

        try {
            profile = retrieveProfileInternal(address, unidentifiedAccess);
        } catch (NonSuccessfulResponseCodeException e) {
            if (unidentifiedAccess.isPresent()) {
                profile = retrieveProfileInternal(address, Optional.absent());
            } else {
                throw e;
            }
        }

        return profile;
    }

    public static @Nullable String decryptName(@NonNull byte[] profileKey, @Nullable String encryptedName)
            throws InvalidCiphertextException, IOException
    {
        if (encryptedName == null) {
            return null;
        }

        ProfileCipher profileCipher = new ProfileCipher(profileKey);
        return new String(profileCipher.decryptName(Base64.decode(encryptedName)));
    }

    @WorkerThread
    private static SignalServiceProfile retrieveProfileInternal(@NonNull SignalServiceAddress address, Optional<UnidentifiedAccess> unidentifiedAccess)
            throws IOException
    {
        SignalServiceMessagePipe authPipe         = IncomingMessageObserver.getPipe();
        SignalServiceMessagePipe unidentifiedPipe = IncomingMessageObserver.getUnidentifiedPipe();
        SignalServiceMessagePipe pipe             = unidentifiedPipe != null && unidentifiedAccess.isPresent() ? unidentifiedPipe
                : authPipe;

        if (pipe != null) {
            try {
                return pipe.getProfile(address, unidentifiedAccess);
            } catch (IOException e) {
                Log.w(TAG, e);
            }
        }

        SignalServiceMessageReceiver receiver = ApplicationDependencies.getSignalServiceMessageReceiver();
        return receiver.retrieveProfile(address, unidentifiedAccess);
    }

    private static Optional<UnidentifiedAccess> getUnidentifiedAccess(@NonNull Context context, @NonNull Recipient recipient) {
        Optional<UnidentifiedAccessPair> unidentifiedAccess = UnidentifiedAccessUtil.getAccessFor(context, recipient);

        if (unidentifiedAccess.isPresent()) {
            return unidentifiedAccess.get().getTargetUnidentifiedAccess();
        }

        return Optional.absent();
    }
}