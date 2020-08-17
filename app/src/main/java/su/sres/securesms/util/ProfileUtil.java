package su.sres.securesms.util;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.signal.zkgroup.VerificationFailedException;
import org.signal.zkgroup.profiles.ProfileKey;
import su.sres.securesms.crypto.ProfileKeyUtil;
import su.sres.securesms.crypto.UnidentifiedAccessUtil;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.logging.Log;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientUtil;
import su.sres.securesms.messages.IncomingMessageObserver;
import org.whispersystems.libsignal.util.guava.Optional;
import su.sres.signalservice.api.SignalServiceMessagePipe;
import su.sres.signalservice.api.SignalServiceMessageReceiver;
import su.sres.signalservice.api.crypto.InvalidCiphertextException;
import su.sres.signalservice.api.crypto.ProfileCipher;
import su.sres.signalservice.api.crypto.UnidentifiedAccess;
import su.sres.signalservice.api.crypto.UnidentifiedAccessPair;
import su.sres.signalservice.api.profiles.ProfileAndCredential;
import su.sres.signalservice.api.profiles.SignalServiceProfile;
import su.sres.signalservice.api.push.SignalServiceAddress;
import su.sres.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;

import java.io.IOException;

/**
 * Aids in the retrieval and decryption of profiles.
 */
public final class ProfileUtil {

    private ProfileUtil() {
    }

    private static final String TAG = Log.tag(ProfileUtil.class);

    @WorkerThread
    public static @NonNull ProfileAndCredential retrieveProfile(@NonNull Context context,
                                                                @NonNull Recipient recipient,
                                                                @NonNull SignalServiceProfile.RequestType requestType)
            throws IOException
    {
        SignalServiceAddress         address            = RecipientUtil.toSignalServiceAddress(context, recipient);
        Optional<UnidentifiedAccess> unidentifiedAccess = getUnidentifiedAccess(context, recipient);
        Optional<ProfileKey>         profileKey         = ProfileKeyUtil.profileKeyOptional(recipient.getProfileKey());

        ProfileAndCredential profile;

        try {
            profile = retrieveProfileInternal(address, profileKey, unidentifiedAccess, requestType);
        } catch (NonSuccessfulResponseCodeException e) {
            if (unidentifiedAccess.isPresent()) {
                profile = retrieveProfileInternal(address, profileKey, Optional.absent(), requestType);
            } else {
                throw e;
            }
        }

        return profile;
    }

    public static @Nullable String decryptName(@NonNull ProfileKey profileKey, @Nullable String encryptedName)
            throws InvalidCiphertextException, IOException
    {
        if (encryptedName == null) {
            return null;
        }

        ProfileCipher profileCipher = new ProfileCipher(profileKey);
        return new String(profileCipher.decryptName(Base64.decode(encryptedName)));
    }

    @WorkerThread
    private static @NonNull ProfileAndCredential retrieveProfileInternal(@NonNull SignalServiceAddress address,
                                                                         @NonNull Optional<ProfileKey> profileKey,
                                                                         @NonNull Optional<UnidentifiedAccess> unidentifiedAccess,
                                                                         @NonNull SignalServiceProfile.RequestType requestType)
            throws IOException
    {
        SignalServiceMessagePipe authPipe         = IncomingMessageObserver.getPipe();
        SignalServiceMessagePipe unidentifiedPipe = IncomingMessageObserver.getUnidentifiedPipe();
        SignalServiceMessagePipe pipe             = unidentifiedPipe != null && unidentifiedAccess.isPresent() ? unidentifiedPipe
                : authPipe;

        if (pipe != null) {
            try {
                return pipe.getProfile(address, profileKey, unidentifiedAccess, requestType);
            } catch (IOException e) {
                Log.w(TAG, "Websocket request failed. Falling back to REST.", e);
            }
        }

        SignalServiceMessageReceiver receiver = ApplicationDependencies.getSignalServiceMessageReceiver();
        try {
            return receiver.retrieveProfile(address, profileKey, unidentifiedAccess, requestType);
        } catch (VerificationFailedException e) {
            throw new IOException("Verification Problem", e);
        }
    }

    private static Optional<UnidentifiedAccess> getUnidentifiedAccess(@NonNull Context context, @NonNull Recipient recipient) {
        Optional<UnidentifiedAccessPair> unidentifiedAccess = UnidentifiedAccessUtil.getAccessFor(context, recipient);

        if (unidentifiedAccess.isPresent()) {
            return unidentifiedAccess.get().getTargetUnidentifiedAccess();
        }

        return Optional.absent();
    }
}