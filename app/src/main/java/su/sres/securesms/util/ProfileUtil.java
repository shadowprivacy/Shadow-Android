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
import su.sres.signalservice.api.push.exceptions.NotFoundException;
import su.sres.signalservice.api.push.exceptions.PushNetworkException;
import su.sres.signalservice.internal.util.concurrent.CascadingFuture;
import su.sres.signalservice.internal.util.concurrent.ListenableFuture;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Aids in the retrieval and decryption of profiles.
 */
public final class ProfileUtil {

    private ProfileUtil() {}

    @WorkerThread
    public static @NonNull ProfileAndCredential retrieveProfileSync(@NonNull Context context,
                                                                    @NonNull Recipient recipient,
                                                                    @NonNull SignalServiceProfile.RequestType requestType)
            throws IOException
    {
        try {
            return retrieveProfile(context, recipient, requestType).get(10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof PushNetworkException) {
                throw (PushNetworkException) e.getCause();
            } else {
                throw new IOException(e);
            }
        } catch (InterruptedException | TimeoutException e) {
            throw new PushNetworkException(e);
        }

    }

    public static @NonNull
    ListenableFuture<ProfileAndCredential> retrieveProfile(@NonNull Context context,
                                                           @NonNull Recipient recipient,
                                                           @NonNull SignalServiceProfile.RequestType requestType)
    {
        SignalServiceAddress         address            = RecipientUtil.toSignalServiceAddress(context, recipient);
        Optional<UnidentifiedAccess> unidentifiedAccess = getUnidentifiedAccess(context, recipient);
        Optional<ProfileKey>         profileKey         = ProfileKeyUtil.profileKeyOptional(recipient.getProfileKey());

        if (unidentifiedAccess.isPresent()) {
            return new CascadingFuture<>(Arrays.asList(() -> getPipeRetrievalFuture(address, profileKey, unidentifiedAccess, requestType),
                    () -> getSocketRetrievalFuture(address, profileKey, unidentifiedAccess, requestType),
                    () -> getPipeRetrievalFuture(address, profileKey, Optional.absent(), requestType),
                    () -> getSocketRetrievalFuture(address, profileKey, Optional.absent(), requestType)),
                    e -> !(e instanceof NotFoundException));
        } else {
            return new CascadingFuture<>(Arrays.asList(() -> getPipeRetrievalFuture(address, profileKey, Optional.absent(), requestType),
                    () -> getSocketRetrievalFuture(address, profileKey, Optional.absent(), requestType)),
                    e -> !(e instanceof NotFoundException));
        }
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
    private static @NonNull ListenableFuture<ProfileAndCredential> getPipeRetrievalFuture(@NonNull SignalServiceAddress address,
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
            return pipe.getProfile(address, profileKey, unidentifiedAccess, requestType);
        }

        throw new IOException("No pipe available!");
    }

    private static @NonNull ListenableFuture<ProfileAndCredential> getSocketRetrievalFuture(@NonNull SignalServiceAddress address,
                                                                                            @NonNull Optional<ProfileKey> profileKey,
                                                                                            @NonNull Optional<UnidentifiedAccess> unidentifiedAccess,
                                                                                            @NonNull SignalServiceProfile.RequestType requestType)
    {

        SignalServiceMessageReceiver receiver = ApplicationDependencies.getSignalServiceMessageReceiver();
        return receiver.retrieveProfile(address, profileKey, unidentifiedAccess, requestType);
    }

    private static Optional<UnidentifiedAccess> getUnidentifiedAccess(@NonNull Context context, @NonNull Recipient recipient) {
        Optional<UnidentifiedAccessPair> unidentifiedAccess = UnidentifiedAccessUtil.getAccessFor(context, recipient);

        if (unidentifiedAccess.isPresent()) {
            return unidentifiedAccess.get().getTargetUnidentifiedAccess();
        }

        return Optional.absent();
    }
}