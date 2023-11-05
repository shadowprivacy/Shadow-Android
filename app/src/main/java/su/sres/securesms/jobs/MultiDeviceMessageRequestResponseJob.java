package su.sres.securesms.jobs;

import androidx.annotation.NonNull;

import su.sres.securesms.crypto.UnidentifiedAccessUtil;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobmanager.impl.NetworkConstraint;
import su.sres.core.util.logging.Log;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.recipients.RecipientUtil;
import su.sres.securesms.util.TextSecurePreferences;
import su.sres.signalservice.api.SignalServiceMessageSender;
import su.sres.signalservice.api.crypto.UntrustedIdentityException;
import su.sres.signalservice.api.messages.multidevice.MessageRequestResponseMessage;
import su.sres.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import su.sres.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class MultiDeviceMessageRequestResponseJob extends BaseJob {

    public static final String KEY = "MultiDeviceMessageRequestResponseJob";

    private static final String TAG = Log.tag(MultiDeviceMessageRequestResponseJob.class);

    private static final String KEY_THREAD_RECIPIENT = "thread_recipient";
    private static final String KEY_TYPE             = "type";

    private final RecipientId threadRecipient;
    private final Type        type;

    public static @NonNull MultiDeviceMessageRequestResponseJob forAccept(@NonNull RecipientId threadRecipient) {
        return new MultiDeviceMessageRequestResponseJob(threadRecipient, Type.ACCEPT);
    }

    public static @NonNull MultiDeviceMessageRequestResponseJob forDelete(@NonNull RecipientId threadRecipient) {
        return new MultiDeviceMessageRequestResponseJob(threadRecipient, Type.DELETE);
    }

    public static @NonNull MultiDeviceMessageRequestResponseJob forBlock(@NonNull RecipientId threadRecipient) {
        return new MultiDeviceMessageRequestResponseJob(threadRecipient, Type.BLOCK);
    }

    public static @NonNull MultiDeviceMessageRequestResponseJob forBlockAndDelete(@NonNull RecipientId threadRecipient) {
        return new MultiDeviceMessageRequestResponseJob(threadRecipient, Type.BLOCK_AND_DELETE);
    }

    private MultiDeviceMessageRequestResponseJob(@NonNull RecipientId threadRecipient, @NonNull Type type) {
        this(new Parameters.Builder()
                .setQueue("MultiDeviceMessageRequestResponseJob")
                .addConstraint(NetworkConstraint.KEY)
                .setMaxAttempts(Parameters.UNLIMITED)
                .setLifespan(TimeUnit.DAYS.toMillis(1))
                .build(), threadRecipient, type);

    }

    private MultiDeviceMessageRequestResponseJob(@NonNull Parameters parameters,
                                                 @NonNull RecipientId threadRecipient,
                                                 @NonNull Type type)
    {
        super(parameters);
        this.threadRecipient = threadRecipient;
        this.type            = type;
    }

    @Override
    public @NonNull Data serialize() {
        return new Data.Builder().putString(KEY_THREAD_RECIPIENT, threadRecipient.serialize())
                .putInt(KEY_TYPE, type.serialize())
                .build();
    }

    @Override
    public @NonNull String getFactoryKey() {
        return KEY;
    }

    @Override
    public void onRun() throws IOException, UntrustedIdentityException {
        if (!TextSecurePreferences.isMultiDevice(context)) {
            Log.i(TAG, "Not multi device, aborting...");
            return;
        }

        SignalServiceMessageSender messageSender = ApplicationDependencies.getSignalServiceMessageSender();
        Recipient                  recipient     = Recipient.resolved(threadRecipient);

        if (!recipient.hasServiceIdentifier()) {
            Log.i(TAG, "Queued for recipient without service identifier");
            return;
        }

        MessageRequestResponseMessage response;

        if (recipient.isGroup()) {
            response = MessageRequestResponseMessage.forGroup(recipient.getGroupId().get().getDecodedId(), localToRemoteType(type));
        } else {
            response = MessageRequestResponseMessage.forIndividual(RecipientUtil.toSignalServiceAddress(context, recipient), localToRemoteType(type));
        }

        messageSender.sendMessage(SignalServiceSyncMessage.forMessageRequestResponse(response),
                UnidentifiedAccessUtil.getAccessForSync(context));
    }

    private static MessageRequestResponseMessage.Type localToRemoteType(@NonNull Type type) {
        switch (type) {
            case ACCEPT:           return MessageRequestResponseMessage.Type.ACCEPT;
            case DELETE:           return MessageRequestResponseMessage.Type.DELETE;
            case BLOCK:            return MessageRequestResponseMessage.Type.BLOCK;
            case BLOCK_AND_DELETE: return MessageRequestResponseMessage.Type.BLOCK_AND_DELETE;
            default:               return MessageRequestResponseMessage.Type.UNKNOWN;
        }
    }

    @Override
    public boolean onShouldRetry(@NonNull Exception e) {
        return e instanceof PushNetworkException;
    }

    @Override
    public void onFailure() {
    }

    private enum Type {
        UNKNOWN(0), ACCEPT(1), DELETE(2), BLOCK(3), BLOCK_AND_DELETE(4);

        private final int value;

        Type(int value) {
            this.value = value;
        }

        int serialize() {
            return value;
        }

        static @NonNull Type deserialize(int value) {
            for (Type type : Type.values()) {
                if (type.value == value) {
                    return type;
                }
            }
            throw new AssertionError("Unknown type: " + value);
        }
    }

    public static final class Factory implements Job.Factory<MultiDeviceMessageRequestResponseJob> {
        @Override
        public @NonNull
        MultiDeviceMessageRequestResponseJob create(@NonNull Parameters parameters, @NonNull Data data) {
            RecipientId threadRecipient = RecipientId.from(data.getString(KEY_THREAD_RECIPIENT));
            Type        type            = Type.deserialize(data.getInt(KEY_TYPE));

            return new MultiDeviceMessageRequestResponseJob(parameters, threadRecipient, type);
        }
    }
}