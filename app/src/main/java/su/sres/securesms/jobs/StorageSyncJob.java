package su.sres.securesms.jobs;

import android.content.Context;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import su.sres.securesms.contacts.sync.StorageSyncHelper;
import su.sres.securesms.contacts.sync.StorageSyncHelper.KeyDifferenceResult;
import su.sres.securesms.contacts.sync.StorageSyncHelper.LocalWriteResult;
import su.sres.securesms.contacts.sync.StorageSyncHelper.MergeResult;
import su.sres.securesms.contacts.sync.StorageSyncHelper.WriteOperationResult;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.RecipientDatabase;
import su.sres.securesms.database.RecipientDatabase.RecipientSettings;
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
import org.whispersystems.libsignal.util.guava.Optional;
import su.sres.signalservice.api.SignalServiceAccountManager;
import su.sres.signalservice.api.storage.StorageKey;
import su.sres.signalservice.api.push.exceptions.PushNetworkException;
import su.sres.signalservice.api.storage.SignalStorageManifest;
import su.sres.signalservice.api.storage.SignalStorageRecord;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Does a full sync of our local storage state with the remote storage state. Will write any pending
 * local changes and resolve any conflicts with remote storage.
 *
 * This should be performed whenever a change is made locally, or whenever we want to retrieve
 * changes that have been made remotely.
 */
public class StorageSyncJob extends BaseJob {

    public static final String KEY       = "StorageSyncJob";
    public static final String QUEUE_KEY = "StorageSyncingJobs";

    private static final String TAG = Log.tag(StorageSyncJob.class);

    private static final long REFRESH_INTERVAL = TimeUnit.HOURS.toMillis(2);

    public StorageSyncJob() {
        this(new Job.Parameters.Builder().addConstraint(NetworkConstraint.KEY)
                .setQueue(QUEUE_KEY)
                .setMaxInstances(1)
                .setLifespan(TimeUnit.DAYS.toMillis(1))
                .build());
    }

    public static void scheduleIfNecessary() {
        long timeSinceLastSync = System.currentTimeMillis() - SignalStore.storageServiceValues().getLastSyncTime();

        if (timeSinceLastSync > REFRESH_INTERVAL) {
            Log.d(TAG, "Scheduling a sync. Last sync was " + timeSinceLastSync + " ms ago.");
            ApplicationDependencies.getJobManager().add(new StorageSyncJob());
        } else {
            Log.d(TAG, "No need for sync. Last sync was " + timeSinceLastSync + " ms ago.");
        }
    }

    private StorageSyncJob(@NonNull Parameters parameters) {
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
        if (!FeatureFlags.storageService()) {
            Log.i(TAG, "Not enabled. Skipping.");
            return;
        }

        try {
            boolean needsMultiDeviceSync = performSync();

            if (TextSecurePreferences.isMultiDevice(context) && needsMultiDeviceSync) {
                ApplicationDependencies.getJobManager().add(new MultiDeviceStorageSyncRequestJob());
            }

            SignalStore.storageServiceValues().onSyncCompleted();
        } catch (InvalidKeyException e) {
            Log.w(TAG, "Failed to decrypt remote storage! Force-pushing and syncing the storage key to linked devices.", e);

            ApplicationDependencies.getJobManager().startChain(new MultiDeviceKeysUpdateJob())
                    .then(new StorageForcePushJob())
                    .then(new MultiDeviceStorageSyncRequestJob())
                    .enqueue();
        } finally {
            if (!SignalStore.storageServiceValues().hasFirstStorageSyncCompleted()) {
                SignalStore.storageServiceValues().setFirstStorageSyncCompleted(true);
                ApplicationDependencies.getJobManager().add(new DirectoryRefreshJob(false));
            }
        }
    }

    @Override
    protected boolean onShouldRetry(@NonNull Exception e) {
        return e instanceof PushNetworkException || e instanceof RetryLaterException;
    }

    @Override
    public void onFailure() {
    }

    private boolean performSync() throws IOException, RetryLaterException, InvalidKeyException {
        SignalServiceAccountManager accountManager     = ApplicationDependencies.getSignalServiceAccountManager();
        RecipientDatabase           recipientDatabase  = DatabaseFactory.getRecipientDatabase(context);
        StorageKeyDatabase          storageKeyDatabase = DatabaseFactory.getStorageKeyDatabase(context);
        StorageKey                  storageServiceKey  = SignalStore.storageServiceValues().getOrCreateStorageMasterKey().deriveStorageServiceKey();

        boolean                         needsMultiDeviceSync  = false;
        long                            localManifestVersion  = TextSecurePreferences.getStorageManifestVersion(context);
        Optional<SignalStorageManifest> remoteManifest        = accountManager.getStorageManifestIfDifferentVersion(storageServiceKey, localManifestVersion);
        long                            remoteManifestVersion = remoteManifest.transform(SignalStorageManifest::getVersion).or(localManifestVersion);

        Log.i(TAG, "Our version: " + localManifestVersion + ", their version: " + remoteManifestVersion);

        if (remoteManifest.isPresent() && remoteManifestVersion > localManifestVersion) {
            Log.i(TAG, "[Remote Newer] Newer manifest version found!");

            List<byte[]>        allLocalStorageKeys = getAllLocalStorageKeys(context);
            KeyDifferenceResult keyDifference       = StorageSyncHelper.findKeyDifference(remoteManifest.get().getStorageKeys(), allLocalStorageKeys);

            if (!keyDifference.isEmpty()) {
                Log.i(TAG, "[Remote Newer] There's a difference in keys. Local-only: " + keyDifference.getLocalOnlyKeys().size() + ", Remote-only: " + keyDifference.getRemoteOnlyKeys().size());

                List<SignalStorageRecord> localOnly            = buildLocalStorageRecords(context, keyDifference.getLocalOnlyKeys());
                List<SignalStorageRecord> remoteOnly           = accountManager.readStorageRecords(storageServiceKey, keyDifference.getRemoteOnlyKeys());
                MergeResult               mergeResult          = StorageSyncHelper.resolveConflict(remoteOnly, localOnly);
                WriteOperationResult      writeOperationResult = StorageSyncHelper.createWriteOperation(remoteManifest.get().getVersion(), allLocalStorageKeys, mergeResult);

                Log.i(TAG, "[Remote Newer] MergeResult :: " + mergeResult);

                if (!writeOperationResult.isEmpty()) {
                    Log.i(TAG, "[Remote Newer] WriteOperationResult :: " + writeOperationResult);
                    Log.i(TAG, "[Remote Newer] We have something to write remotely.");

                    if (writeOperationResult.getManifest().getStorageKeys().size() != remoteManifest.get().getStorageKeys().size() + writeOperationResult.getInserts().size() - writeOperationResult.getDeletes().size()) {
                        Log.w(TAG, String.format(Locale.ENGLISH, "Bad storage key management! originalRemoteKeys: %d, newRemoteKeys: %d, insertedKeys: %d, deletedKeys: %d",
                                remoteManifest.get().getStorageKeys().size(), writeOperationResult.getManifest().getStorageKeys().size(), writeOperationResult.getInserts().size(), writeOperationResult.getDeletes().size()));
                    }

                    Optional<SignalStorageManifest> conflict = accountManager.writeStorageRecords(storageServiceKey, writeOperationResult.getManifest(), writeOperationResult.getInserts(), writeOperationResult.getDeletes());

                    if (conflict.isPresent()) {
                        Log.w(TAG, "[Remote Newer] Hit a conflict when trying to resolve the conflict! Retrying.");
                        throw new RetryLaterException();
                    }

                    remoteManifestVersion = writeOperationResult.getManifest().getVersion();
                } else {
                    Log.i(TAG, "[Remote Newer] After resolving the conflict, all changes are local. No remote writes needed.");
                }

                recipientDatabase.applyStorageSyncUpdates(mergeResult.getLocalContactInserts(), mergeResult.getLocalContactUpdates(), mergeResult.getLocalGroupV1Inserts(), mergeResult.getLocalGroupV1Updates());
                storageKeyDatabase.applyStorageSyncUpdates(mergeResult.getLocalUnknownInserts(), mergeResult.getLocalUnknownDeletes());
                needsMultiDeviceSync = true;

                Log.i(TAG, "[Remote Newer] Updating local manifest version to: " + remoteManifestVersion);
                TextSecurePreferences.setStorageManifestVersion(context, remoteManifestVersion);
            } else {
                Log.i(TAG, "[Remote Newer] Remote version was newer, but our local data matched.");
                Log.i(TAG, "[Remote Newer] Updating local manifest version to: " + remoteManifest.get().getVersion());
                TextSecurePreferences.setStorageManifestVersion(context, remoteManifest.get().getVersion());
            }
        }

        localManifestVersion = TextSecurePreferences.getStorageManifestVersion(context);

        List<byte[]>               allLocalStorageKeys = recipientDatabase.getAllStorageSyncKeys();
        List<RecipientSettings>    pendingUpdates      = recipientDatabase.getPendingRecipientSyncUpdates();
        List<RecipientSettings>    pendingInsertions   = recipientDatabase.getPendingRecipientSyncInsertions();
        List<RecipientSettings>    pendingDeletions    = recipientDatabase.getPendingRecipientSyncDeletions();
        Optional<LocalWriteResult> localWriteResult    = StorageSyncHelper.buildStorageUpdatesForLocal(localManifestVersion,
                allLocalStorageKeys,
                pendingUpdates,
                pendingInsertions,
                pendingDeletions);

        if (localWriteResult.isPresent()) {
            Log.i(TAG, String.format(Locale.ENGLISH, "[Local Changes] Local changes present. %d updates, %d inserts, %d deletes.", pendingUpdates.size(), pendingInsertions.size(), pendingDeletions.size()));

            WriteOperationResult localWrite = localWriteResult.get().getWriteResult();

            Log.i(TAG, "[Local Changes] WriteOperationResult :: " + localWrite);

            if (localWrite.isEmpty()) {
                throw new AssertionError("Decided there were local writes, but our write result was empty!");
            }
            Optional<SignalStorageManifest> conflict   = accountManager.writeStorageRecords(storageServiceKey, localWrite.getManifest(), localWrite.getInserts(), localWrite.getDeletes());

            if (conflict.isPresent()) {
                Log.w(TAG, "[Local Changes] Hit a conflict when trying to upload our local writes! Retrying.");
                throw new RetryLaterException();
            }

            List<RecipientId> clearIds = new ArrayList<>(pendingUpdates.size() + pendingInsertions.size() + pendingDeletions.size());

            clearIds.addAll(Stream.of(pendingUpdates).map(RecipientSettings::getId).toList());
            clearIds.addAll(Stream.of(pendingInsertions).map(RecipientSettings::getId).toList());
            clearIds.addAll(Stream.of(pendingDeletions).map(RecipientSettings::getId).toList());

            recipientDatabase.clearDirtyState(clearIds);
            recipientDatabase.updateStorageKeys(localWriteResult.get().getStorageKeyUpdates());

            needsMultiDeviceSync = true;

            Log.i(TAG, "[Local Changes] Updating local manifest version to: " + localWriteResult.get().getWriteResult().getManifest().getVersion());
            TextSecurePreferences.setStorageManifestVersion(context, localWriteResult.get().getWriteResult().getManifest().getVersion());
        } else {
            Log.i(TAG, "[Local Changes] No local changes.");
        }

        return needsMultiDeviceSync;
    }

    private static @NonNull List<byte[]> getAllLocalStorageKeys(@NonNull Context context) {
        return Util.concatenatedList(DatabaseFactory.getRecipientDatabase(context).getAllStorageSyncKeys(),
                DatabaseFactory.getStorageKeyDatabase(context).getAllKeys());
    }

    private static @NonNull List<SignalStorageRecord> buildLocalStorageRecords(@NonNull Context context, @NonNull List<byte[]> keys) {
        RecipientDatabase  recipientDatabase  = DatabaseFactory.getRecipientDatabase(context);
        StorageKeyDatabase storageKeyDatabase = DatabaseFactory.getStorageKeyDatabase(context);

        List<SignalStorageRecord> records = new ArrayList<>(keys.size());

        for (byte[] key : keys) {
            SignalStorageRecord record = Optional.fromNullable(recipientDatabase.getByStorageSyncKey(key))
                    .transform(StorageSyncHelper::localToRemoteRecord)
                    .or(() -> storageKeyDatabase.getByKey(key));
            records.add(record);
        }

        return records;
    }

    public static final class Factory implements Job.Factory<StorageSyncJob> {

        @Override
        public @NonNull StorageSyncJob create(@NonNull Parameters parameters, @NonNull Data data) {
            return new StorageSyncJob(parameters);
        }
    }
}