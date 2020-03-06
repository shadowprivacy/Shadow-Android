package su.sres.securesms.jobs;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import su.sres.securesms.contacts.sync.StorageSyncHelper;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.RecipientDatabase;
import su.sres.securesms.database.StorageKeyDatabase;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobmanager.impl.NetworkConstraint;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.securesms.logging.Log;
import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.transport.RetryLaterException;
import su.sres.securesms.util.FeatureFlags;
import su.sres.securesms.util.TextSecurePreferences;
import su.sres.securesms.util.Util;
import org.whispersystems.libsignal.InvalidKeyException;
import su.sres.signalservice.api.SignalServiceAccountManager;
import su.sres.signalservice.api.push.exceptions.PushNetworkException;
import su.sres.signalservice.api.storage.SignalStorageManifest;
import su.sres.signalservice.api.storage.SignalStorageRecord;
import su.sres.signalservice.api.storage.SignalStorageUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Forces remote storage to match our local state. This should only be done after a key change or
 * when we detect that the remote data is badly-encrypted.
 */
public class StorageForcePushJob extends BaseJob {

    public static final String KEY = "StorageForcePushJob";

    private static final String TAG = Log.tag(StorageForcePushJob.class);

    public StorageForcePushJob() {
        this(new Parameters.Builder().addConstraint(NetworkConstraint.KEY)
                .setQueue(StorageSyncJob.QUEUE_KEY)
                .setMaxInstances(1)
                .setLifespan(TimeUnit.DAYS.toMillis(1))
                .build());
    }

    private StorageForcePushJob(@NonNull Parameters parameters) {
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
    protected void onRun() throws IOException, RetryLaterException {
        if (!FeatureFlags.STORAGE_SERVICE) throw new AssertionError();

        byte[] kbsMasterKey = SignalStore.kbsValues().getMasterKey();

        if (kbsMasterKey == null) {
            Log.w(TAG, "No KBS master key is set! Must abort.");
            return;
        }

        byte[]                      storageServiceKey  = SignalStorageUtil.computeStorageServiceKey(kbsMasterKey);
        SignalServiceAccountManager accountManager     = ApplicationDependencies.getSignalServiceAccountManager();
        RecipientDatabase           recipientDatabase  = DatabaseFactory.getRecipientDatabase(context);
        StorageKeyDatabase          storageKeyDatabase = DatabaseFactory.getStorageKeyDatabase(context);

        long                     currentVersion = accountManager.getStorageManifestVersion();
        Map<RecipientId, byte[]> oldContactKeys = recipientDatabase.getAllStorageSyncKeysMap();
        List<byte[]>             oldUnknownKeys = storageKeyDatabase.getAllKeys();

        long                      newVersion     = currentVersion + 1;
        Map<RecipientId, byte[]>  newContactKeys = generateNewKeys(oldContactKeys);
        List<byte[]>              keysToDelete   = Util.concatenatedList(new ArrayList<>(oldContactKeys.values()), oldUnknownKeys);
        List<SignalStorageRecord> inserts        = Stream.of(oldContactKeys.keySet())
                .map(recipientDatabase::getRecipientSettings)
                .withoutNulls()
                .map(StorageSyncHelper::localToRemoteContact)
                .map(r -> SignalStorageRecord.forContact(r.getKey(), r))
                .toList();

        SignalStorageManifest manifest = new SignalStorageManifest(newVersion, new ArrayList<>(newContactKeys.values()));

        try {
            accountManager.writeStorageRecords(storageServiceKey, manifest, inserts, keysToDelete);
        } catch (InvalidKeyException e) {
            Log.w(TAG, "Hit an invalid key exception, which likely indicates a conflict.");
            throw new RetryLaterException();
        }

        TextSecurePreferences.setStorageManifestVersion(context, newVersion);
        recipientDatabase.applyStorageSyncKeyUpdates(newContactKeys);
        storageKeyDatabase.deleteAll();
    }

    @Override
    protected boolean onShouldRetry(@NonNull Exception e) {
        return e instanceof PushNetworkException || e instanceof RetryLaterException;
    }

    @Override
    public void onFailure() {
    }

    private static @NonNull Map<RecipientId, byte[]> generateNewKeys(@NonNull Map<RecipientId, byte[]> oldKeys) {
        Map<RecipientId, byte[]> out = new HashMap<>();

        for (Map.Entry<RecipientId, byte[]> entry : oldKeys.entrySet()) {
            out.put(entry.getKey(), StorageSyncHelper.generateKey());
        }

        return out;
    }

    public static final class Factory implements Job.Factory<StorageForcePushJob> {

        @Override
        public @NonNull
        StorageForcePushJob create(@NonNull Parameters parameters, @NonNull Data data) {
            return new StorageForcePushJob(parameters);
        }
    }
}