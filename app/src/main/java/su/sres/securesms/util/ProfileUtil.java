package su.sres.securesms.util;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.signal.zkgroup.profiles.ProfileKey;

import su.sres.core.util.logging.Log;
import su.sres.securesms.crypto.ProfileKeyUtil;
import su.sres.securesms.crypto.UnidentifiedAccessUtil;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.RecipientDatabase;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.profiles.AvatarHelper;
import su.sres.securesms.profiles.ProfileName;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientUtil;
import su.sres.securesms.messages.IncomingMessageObserver;
import org.whispersystems.libsignal.util.guava.Optional;

import su.sres.signalservice.api.SignalServiceAccountManager;
import su.sres.signalservice.api.SignalServiceMessagePipe;
import su.sres.signalservice.api.SignalServiceMessageReceiver;
import su.sres.signalservice.api.crypto.InvalidCiphertextException;
import su.sres.signalservice.api.crypto.ProfileCipher;
import su.sres.signalservice.api.crypto.UnidentifiedAccess;
import su.sres.signalservice.api.crypto.UnidentifiedAccessPair;
import su.sres.signalservice.api.profiles.ProfileAndCredential;
import su.sres.signalservice.api.profiles.SignalServiceProfile;
import su.sres.signalservice.api.push.SignalServiceAddress;
import su.sres.signalservice.api.push.exceptions.NotFoundException;
import su.sres.signalservice.api.push.exceptions.PushNetworkException;
import su.sres.signalservice.api.util.StreamDetails;
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

    private static final String TAG = Log.tag(ProfileUtil.class);

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
            } else if (e.getCause() instanceof NotFoundException) {
                throw (NotFoundException) e.getCause();
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
        SignalServiceAddress         address            = toSignalServiceAddress(context, recipient);
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

    /**
     * Uploads the profile based on all state that's written to disk, except we'll use the provided
     * profile name instead. This is useful when you want to ensure that the profile has been uploaded
     * successfully before persisting the change to disk.
     */
    public static void uploadProfileWithName(@NonNull Context context, @NonNull ProfileName profileName) throws IOException {
        uploadProfile(context,
                profileName,
                Optional.fromNullable(Recipient.self().getAbout()).or(""),
                Optional.fromNullable(Recipient.self().getAboutEmoji()).or(""));
    }

    /**
     * Uploads the profile based on all state that's written to disk, except we'll use the provided
     * about/emoji instead. This is useful when you want to ensure that the profile has been uploaded
     * successfully before persisting the change to disk.
     */
    public static void uploadProfileWithAbout(@NonNull Context context, @NonNull String about, @NonNull String emoji) throws IOException {
        uploadProfile(context,
                Recipient.self().getProfileName(),
                about,
                emoji);
    }

    /**
     * Uploads the profile based on all state that's already written to disk.
     */
    public static void uploadProfile(@NonNull Context context) throws IOException {
        uploadProfile(context,
                Recipient.self().getProfileName(),
                Optional.fromNullable(Recipient.self().getAbout()).or(""),
                Optional.fromNullable(Recipient.self().getAboutEmoji()).or(""));
    }

    public static void uploadProfile(@NonNull Context context,
                                     @NonNull ProfileName profileName,
                                     @Nullable String about,
                                     @Nullable String aboutEmoji)
            throws IOException
    {
        Log.d(TAG, "Uploading " + (!Util.isEmpty(about) ? "non-" : "") + "empty about.");
        Log.d(TAG, "Uploading " + (!Util.isEmpty(aboutEmoji) ? "non-" : "") + "empty emoji.");

        ProfileKey  profileKey  = ProfileKeyUtil.getSelfProfileKey();
        String      avatarPath;

        try (StreamDetails avatar = AvatarHelper.getSelfProfileAvatarStream(context)) {
            SignalServiceAccountManager accountManager = ApplicationDependencies.getSignalServiceAccountManager();
            avatarPath = accountManager.setVersionedProfile(Recipient.self().getUuid().get(), profileKey, profileName.serialize(), about, aboutEmoji, avatar).orNull();
        }

        DatabaseFactory.getRecipientDatabase(context).setProfileAvatar(Recipient.self().getId(), avatarPath);
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
        Optional<UnidentifiedAccessPair> unidentifiedAccess = UnidentifiedAccessUtil.getAccessFor(context, recipient, false);

        if (unidentifiedAccess.isPresent()) {
            return unidentifiedAccess.get().getTargetUnidentifiedAccess();
        }

        return Optional.absent();
    }

    private static @NonNull SignalServiceAddress toSignalServiceAddress(@NonNull Context context, @NonNull Recipient recipient) {
        if (recipient.getRegistered() == RecipientDatabase.RegisteredState.NOT_REGISTERED) {
            return new SignalServiceAddress(recipient.getUuid().orNull(), recipient.getE164().orNull());
        } else {
            return RecipientUtil.toSignalServiceAddressBestEffort(context, recipient);
        }
    }
}