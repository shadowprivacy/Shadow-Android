package su.sres.securesms.jobs;

import androidx.annotation.NonNull;

import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobmanager.impl.NetworkConstraint;
import su.sres.securesms.logging.Log;

import su.sres.securesms.crypto.ProfileKeyUtil;
import su.sres.securesms.crypto.UnidentifiedAccessUtil;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientUtil;
import su.sres.zkgroup.profiles.ProfileKey;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.util.TextSecurePreferences;

import org.whispersystems.libsignal.util.guava.Optional;

import su.sres.signalservice.api.SignalServiceMessageSender;
import su.sres.signalservice.api.crypto.UntrustedIdentityException;
import su.sres.signalservice.api.messages.SignalServiceAttachment;
import su.sres.signalservice.api.messages.SignalServiceAttachmentStream;
import su.sres.signalservice.api.messages.multidevice.ContactsMessage;
import su.sres.signalservice.api.messages.multidevice.DeviceContact;
import su.sres.signalservice.api.messages.multidevice.DeviceContactsOutputStream;
import su.sres.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import su.sres.signalservice.api.push.exceptions.PushNetworkException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class MultiDeviceProfileKeyUpdateJob extends BaseJob {

    public static String KEY = "MultiDeviceProfileKeyUpdateJob";

    private static final String TAG = MultiDeviceProfileKeyUpdateJob.class.getSimpleName();

    public MultiDeviceProfileKeyUpdateJob() {
        this(new Job.Parameters.Builder()
                .addConstraint(NetworkConstraint.KEY)
                .setQueue("MultiDeviceProfileKeyUpdateJob")
                .setLifespan(TimeUnit.DAYS.toMillis(1))
                .setMaxAttempts(Parameters.UNLIMITED)
                .build());
    }

    private MultiDeviceProfileKeyUpdateJob(@NonNull Job.Parameters parameters) {
        super(parameters);
    }

    @Override
    public @NonNull
    Data serialize() {
        return Data.EMPTY;
    }

    @Override
    public @NonNull
    String getFactoryKey() {
        return KEY;
    }

    @Override
    public void onRun() throws IOException, UntrustedIdentityException {
        if (!TextSecurePreferences.isMultiDevice(context)) {
            Log.i(TAG, "Not multi device...");
            return;
        }

        Optional<ProfileKey>       profileKey = Optional.of(ProfileKeyUtil.getSelfProfileKey());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DeviceContactsOutputStream out = new DeviceContactsOutputStream(baos);

        out.write(new DeviceContact(RecipientUtil.toSignalServiceAddress(context, Recipient.self()),
                Optional.absent(),
                Optional.absent(),
                Optional.absent(),
                Optional.absent(),
                profileKey,
                false,
                Optional.absent(),
                Optional.absent(),
                false));

        out.close();

        SignalServiceMessageSender messageSender = ApplicationDependencies.getSignalServiceMessageSender();
        SignalServiceAttachmentStream attachmentStream = SignalServiceAttachment.newStreamBuilder()
                .withStream(new ByteArrayInputStream(baos.toByteArray()))
                .withContentType("application/octet-stream")
                .withLength(baos.toByteArray().length)
                .build();

        SignalServiceSyncMessage syncMessage = SignalServiceSyncMessage.forContacts(new ContactsMessage(attachmentStream, false));

        messageSender.sendMessage(syncMessage, UnidentifiedAccessUtil.getAccessForSync(context));
    }

    @Override
    public boolean onShouldRetry(@NonNull Exception exception) {
        if (exception instanceof PushNetworkException) return true;
        return false;
    }

    @Override
    public void onFailure() {
        Log.w(TAG, "Profile key sync failed!");
    }

    public static final class Factory implements Job.Factory<MultiDeviceProfileKeyUpdateJob> {
        @Override
        public @NonNull
        MultiDeviceProfileKeyUpdateJob create(@NonNull Parameters parameters, @NonNull Data data) {
            return new MultiDeviceProfileKeyUpdateJob(parameters);
        }
    }
}
