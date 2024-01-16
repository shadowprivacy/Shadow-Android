package su.sres.securesms.jobs;

import android.net.Uri;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;

import su.sres.securesms.crypto.UnidentifiedAccessUtil;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.GroupDatabase;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobmanager.impl.NetworkConstraint;
import su.sres.core.util.logging.Log;
import su.sres.securesms.profiles.AvatarHelper;
import su.sres.securesms.providers.BlobProvider;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.recipients.RecipientUtil;
import su.sres.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.util.guava.Optional;
import su.sres.signalservice.api.SignalServiceMessageSender;
import su.sres.signalservice.api.crypto.UntrustedIdentityException;
import su.sres.signalservice.api.messages.SignalServiceAttachment;
import su.sres.signalservice.api.messages.SignalServiceAttachmentStream;
import su.sres.signalservice.api.messages.multidevice.DeviceGroup;
import su.sres.signalservice.api.messages.multidevice.DeviceGroupsOutputStream;
import su.sres.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import su.sres.signalservice.api.push.SignalServiceAddress;
import su.sres.signalservice.api.push.exceptions.PushNetworkException;
import su.sres.signalservice.api.push.exceptions.ServerRejectedException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class MultiDeviceGroupUpdateJob extends BaseJob {

    public static final String KEY = "MultiDeviceGroupUpdateJob";

    private static final String TAG = Log.tag(MultiDeviceGroupUpdateJob.class);

    public MultiDeviceGroupUpdateJob() {
        this(new Job.Parameters.Builder()
                .addConstraint(NetworkConstraint.KEY)
                .setQueue("MultiDeviceGroupUpdateJob")
                .setLifespan(TimeUnit.DAYS.toMillis(1))
                .setMaxAttempts(Parameters.UNLIMITED)
                .build());
    }

    private MultiDeviceGroupUpdateJob(@NonNull Job.Parameters parameters) {
        super(parameters);
    }

    @Override
    public @NonNull String getFactoryKey() {
        return KEY;
    }

    @Override
    public @NonNull Data serialize() {
        return Data.EMPTY;
    }

    @Override
    public void onRun() throws Exception {
        if (!TextSecurePreferences.isMultiDevice(context)) {
            Log.i(TAG, "Not multi device, aborting...");
            return;
        }

        ParcelFileDescriptor[] pipe        = ParcelFileDescriptor.createPipe();
        InputStream            inputStream = new ParcelFileDescriptor.AutoCloseInputStream(pipe[0]);
        Uri                    uri         = BlobProvider.getInstance()
                .forData(inputStream, 0)
                .withFileName("multidevice-group-update")
                .createForSingleSessionOnDiskAsync(context,
                        () -> Log.i(TAG, "Write successful."),
                        e  -> Log.w(TAG, "Error during write.", e));

        try (GroupDatabase.Reader reader = DatabaseFactory.getGroupDatabase(context).getGroups()) {
            DeviceGroupsOutputStream out     = new DeviceGroupsOutputStream(new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]));
            boolean                  hasData = false;

            GroupDatabase.GroupRecord record;

            while ((record = reader.getNext()) != null) {
                if (record.isV1Group()) {
                    List<SignalServiceAddress> members = new LinkedList<>();

                    for (RecipientId member : record.getMembers()) {
                        members.add(RecipientUtil.toSignalServiceAddress(context, Recipient.resolved(member)));
                    }

                    RecipientId               recipientId     = DatabaseFactory.getRecipientDatabase(context).getOrInsertFromPossiblyMigratedGroupId(record.getId());
                    Recipient                 recipient       = Recipient.resolved(recipientId);
                    Optional<Integer>         expirationTimer = recipient.getExpireMessages() > 0 ? Optional.of(recipient.getExpireMessages()) : Optional.absent();
                    Map<RecipientId, Integer> inboxPositions  = DatabaseFactory.getThreadDatabase(context).getInboxPositions();
                    Set<RecipientId>          archived        = DatabaseFactory.getThreadDatabase(context).getArchivedRecipients();

                    out.write(new DeviceGroup(record.getId().getDecodedId(),
                            Optional.fromNullable(record.getTitle()),
                            members,
                            getAvatar(record.getRecipientId()),
                            record.isActive(),
                            expirationTimer,
                            Optional.of(recipient.getColor().serialize()),
                            recipient.isBlocked(),
                            Optional.fromNullable(inboxPositions.get(recipientId)),
                            archived.contains(recipientId)));

                    hasData = true;
                }
            }

            out.close();

            if (hasData) {
                long length = BlobProvider.getInstance().calculateFileSize(context, uri);

                sendUpdate(ApplicationDependencies.getSignalServiceMessageSender(),
                        BlobProvider.getInstance().getStream(context, uri),
                        length);
            } else {
                Log.w(TAG, "No groups present for sync message. Sending an empty update.");

                sendUpdate(ApplicationDependencies.getSignalServiceMessageSender(),
                        null,
                        0);
            }
        } finally {
            BlobProvider.getInstance().delete(context, uri);
        }
    }

    @Override
    public boolean onShouldRetry(@NonNull Exception exception) {
        if (exception instanceof ServerRejectedException) return false;
        if (exception instanceof PushNetworkException) return true;
        return false;
    }

    @Override
    public void onFailure() {

    }

    private void sendUpdate(SignalServiceMessageSender messageSender, InputStream stream, long length)
            throws IOException, UntrustedIdentityException
    {
        SignalServiceAttachmentStream attachmentStream;

        if (length > 0) {
            attachmentStream = SignalServiceAttachment.newStreamBuilder()
                    .withStream(stream)
                    .withContentType("application/octet-stream")
                    .withLength(length)
                    .build();
        } else {
            attachmentStream = SignalServiceAttachment.emptyStream("application/octet-stream");
        }

        messageSender.sendMessage(SignalServiceSyncMessage.forGroups(attachmentStream),
                UnidentifiedAccessUtil.getAccessForSync(context));
    }


    private Optional<SignalServiceAttachmentStream> getAvatar(@NonNull RecipientId recipientId) throws IOException {
        if (!AvatarHelper.hasAvatar(context, recipientId)) return Optional.absent();

        return Optional.of(SignalServiceAttachment.newStreamBuilder()
                .withStream(AvatarHelper.getAvatar(context, recipientId))
                .withContentType("image/*")
                .withLength(AvatarHelper.getAvatarLength(context, recipientId))
                .build());
    }

    private File createTempFile(String prefix) throws IOException {
        File file = File.createTempFile(prefix, "tmp", context.getCacheDir());
        file.deleteOnExit();

        return file;
    }

    public static final class Factory implements Job.Factory<MultiDeviceGroupUpdateJob> {
        @Override
        public @NonNull MultiDeviceGroupUpdateJob create(@NonNull Parameters parameters, @NonNull Data data) {
            return new MultiDeviceGroupUpdateJob(parameters);
        }
    }
}