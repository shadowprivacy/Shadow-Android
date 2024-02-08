package su.sres.securesms.jobs;

import androidx.annotation.NonNull;

import su.sres.securesms.crypto.UnidentifiedAccessUtil;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.StickerDatabase.StickerPackRecordReader;
import su.sres.securesms.database.model.StickerPackRecord;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobmanager.impl.NetworkConstraint;
import su.sres.core.util.logging.Log;
import su.sres.securesms.net.NotPushRegisteredException;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.util.Hex;
import su.sres.securesms.util.TextSecurePreferences;
import su.sres.signalservice.api.SignalServiceMessageSender;
import su.sres.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import su.sres.signalservice.api.messages.multidevice.StickerPackOperationMessage;
import su.sres.signalservice.api.push.exceptions.PushNetworkException;
import su.sres.signalservice.api.push.exceptions.ServerRejectedException;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;



/**
 * Tells a linked desktop about all installed sticker packs.
 */
public class MultiDeviceStickerPackSyncJob extends BaseJob  {

    private static final String TAG = Log.tag(MultiDeviceStickerPackSyncJob.class);

    public static final String KEY = "MultiDeviceStickerPackSyncJob";



    public MultiDeviceStickerPackSyncJob() {
        this(new Parameters.Builder()
                .setQueue("MultiDeviceStickerPackSyncJob")
                .addConstraint(NetworkConstraint.KEY)
                .setLifespan(TimeUnit.DAYS.toMillis(1))
                .build());
    }

    public MultiDeviceStickerPackSyncJob(@NonNull Parameters parameters) {
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
    protected void onRun() throws Exception {
        if (!Recipient.self().isRegistered()) {
            throw new NotPushRegisteredException();
        }

        if (!TextSecurePreferences.isMultiDevice(context)) {
            Log.i(TAG, "Not multi device, aborting...");
            return;
        }

        List<StickerPackOperationMessage> operations = new LinkedList<>();

        try (StickerPackRecordReader reader = new StickerPackRecordReader(DatabaseFactory.getStickerDatabase(context).getInstalledStickerPacks())) {
            StickerPackRecord pack;
            while ((pack = reader.getNext()) != null) {
                byte[] packIdBytes  = Hex.fromStringCondensed(pack.getPackId());
                byte[] packKeyBytes = Hex.fromStringCondensed(pack.getPackKey());

                operations.add(new StickerPackOperationMessage(packIdBytes, packKeyBytes, StickerPackOperationMessage.Type.INSTALL));
            }
        }

        SignalServiceMessageSender messageSender = ApplicationDependencies.getSignalServiceMessageSender();
        messageSender.sendSyncMessage(SignalServiceSyncMessage.forStickerPackOperations(operations),
                                      UnidentifiedAccessUtil.getAccessForSync(context));
    }

    @Override
    protected boolean onShouldRetry(@NonNull Exception e) {
        if (e instanceof ServerRejectedException) return false;
        return e instanceof PushNetworkException;
    }

    @Override
    public void onFailure() {
        Log.w(TAG, "Failed to sync sticker pack operation!");
    }

    public static class Factory implements Job.Factory<MultiDeviceStickerPackSyncJob> {

        @Override
        public @NonNull
        MultiDeviceStickerPackSyncJob create(@NonNull Parameters parameters, @NonNull Data data) {
            return new MultiDeviceStickerPackSyncJob(parameters);
        }
    }
}