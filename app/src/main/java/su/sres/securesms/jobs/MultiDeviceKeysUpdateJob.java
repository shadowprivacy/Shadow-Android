package su.sres.securesms.jobs;

import androidx.annotation.NonNull;

import su.sres.securesms.crypto.UnidentifiedAccessUtil;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobmanager.impl.NetworkConstraint;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.core.util.logging.Log;
import su.sres.securesms.net.NotPushRegisteredException;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.util.guava.Optional;
import su.sres.signalservice.api.SignalServiceMessageSender;
import su.sres.signalservice.api.crypto.UntrustedIdentityException;
import su.sres.signalservice.api.push.exceptions.ServerRejectedException;
import su.sres.signalservice.api.storage.StorageKey;
import su.sres.signalservice.api.messages.multidevice.KeysMessage;
import su.sres.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import su.sres.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;

public class MultiDeviceKeysUpdateJob extends BaseJob {

    public static final String KEY = "MultiDeviceKeysUpdateJob";

    private static final String TAG = Log.tag(MultiDeviceKeysUpdateJob.class);

    public MultiDeviceKeysUpdateJob() {
        this(new Parameters.Builder()
                .setQueue("MultiDeviceKeysUpdateJob")
                .setMaxInstancesForFactory(2)
                .addConstraint(NetworkConstraint.KEY)
                .setMaxAttempts(10)
                .build());

    }

    private MultiDeviceKeysUpdateJob(@NonNull Parameters parameters) {
        super(parameters);
    }

    @Override
    public @NonNull Data serialize() {
        return Data.EMPTY;
    }

    @Override
    public @NonNull String getFactoryKey() {
        return KEY;
    }

    @Override
    public void onRun() throws IOException, UntrustedIdentityException {
        if (!Recipient.self().isRegistered()) {
            throw new NotPushRegisteredException();
        }

        if (!TextSecurePreferences.isMultiDevice(context)) {
            Log.i(TAG, "Not multi device, aborting...");
            return;
        }

        SignalServiceMessageSender messageSender     = ApplicationDependencies.getSignalServiceMessageSender();
        StorageKey                 storageServiceKey = SignalStore.storageService().getOrCreateStorageKey();

        messageSender.sendSyncMessage(SignalServiceSyncMessage.forKeys(new KeysMessage(Optional.fromNullable(storageServiceKey))),
                                      UnidentifiedAccessUtil.getAccessForSync(context));
    }

    @Override
    public boolean onShouldRetry(@NonNull Exception e) {
        if (e instanceof ServerRejectedException) return false;
        return e instanceof PushNetworkException;
    }

    @Override
    public void onFailure() {
    }

    public static final class Factory implements Job.Factory<MultiDeviceKeysUpdateJob> {
        @Override
        public @NonNull MultiDeviceKeysUpdateJob create(@NonNull Parameters parameters, @NonNull Data data) {
            return new MultiDeviceKeysUpdateJob(parameters);
        }
    }
}