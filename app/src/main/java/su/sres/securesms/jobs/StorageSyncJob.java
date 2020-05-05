package su.sres.securesms.jobs;

import android.content.Context;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.storage.StorageSyncHelper;
import su.sres.securesms.storage.StorageSyncHelper.KeyDifferenceResult;
import su.sres.securesms.storage.StorageSyncHelper.LocalWriteResult;
import su.sres.securesms.storage.StorageSyncHelper.MergeResult;
import su.sres.securesms.storage.StorageSyncHelper.WriteOperationResult;
import su.sres.securesms.storage.StorageSyncModels;
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
import su.sres.securesms.storage.StorageSyncValidations;
import su.sres.securesms.transport.RetryLaterException;
import su.sres.securesms.util.FeatureFlags;
import su.sres.securesms.util.TextSecurePreferences;
import su.sres.securesms.util.Util;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.util.guava.Optional;
import su.sres.signalservice.api.SignalServiceAccountManager;
import su.sres.signalservice.api.storage.SignalAccountRecord;
import su.sres.signalservice.api.storage.StorageId;
import su.sres.signalservice.api.storage.StorageKey;
import su.sres.signalservice.api.push.exceptions.PushNetworkException;
import su.sres.signalservice.api.storage.SignalStorageManifest;
import su.sres.signalservice.api.storage.SignalStorageRecord;
import su.sres.signalservice.internal.storage.protos.ManifestRecord;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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

    public StorageSyncJob() {
        this(new Job.Parameters.Builder().addConstraint(NetworkConstraint.KEY)
                .setQueue(QUEUE_KEY)
                .setMaxInstances(1)
                .setLifespan(TimeUnit.DAYS.toMillis(1))
                .build());
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

        if (!TextSecurePreferences.isPushRegistered(context)) {
            Log.i(TAG, "Not registered. Skipping.");
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

            List<StorageId>     allLocalStorageKeys = getAllLocalStorageIds(context);
            KeyDifferenceResult keyDifference       = StorageSyncHelper.findKeyDifference(remoteManifest.get().getStorageIds(), allLocalStorageKeys);

            if (!keyDifference.isEmpty()) {
                Log.i(TAG, "[Remote Newer] There's a difference in keys. Local-only: " + keyDifference.getLocalOnlyKeys().size() + ", Remote-only: " + keyDifference.getRemoteOnlyKeys().size());

                Set<RecipientId>          archivedRecipients   = DatabaseFactory.getThreadDatabase(context).getArchivedRecipients();
                List<SignalStorageRecord> localOnly            = buildLocalStorageRecords(context, keyDifference.getLocalOnlyKeys(), archivedRecipients);
                List<SignalStorageRecord> remoteOnly           = accountManager.readStorageRecords(storageServiceKey, keyDifference.getRemoteOnlyKeys());
                MergeResult               mergeResult          = StorageSyncHelper.resolveConflict(remoteOnly, localOnly);
                WriteOperationResult      writeOperationResult = StorageSyncHelper.createWriteOperation(remoteManifest.get().getVersion(), allLocalStorageKeys, mergeResult);

                StorageSyncValidations.validate(writeOperationResult);

                Log.i(TAG, "[Remote Newer] MergeResult :: " + mergeResult);

                if (!writeOperationResult.isEmpty()) {
                    Log.i(TAG, "[Remote Newer] WriteOperationResult :: " + writeOperationResult);
                    Log.i(TAG, "[Remote Newer] We have something to write remotely.");

                    if (writeOperationResult.getManifest().getStorageIds().size() != remoteManifest.get().getStorageIds().size() + writeOperationResult.getInserts().size() - writeOperationResult.getDeletes().size()) {
                        Log.w(TAG, String.format(Locale.ENGLISH, "Bad storage key management! originalRemoteKeys: %d, newRemoteKeys: %d, insertedKeys: %d, deletedKeys: %d",
                                remoteManifest.get().getStorageIds().size(), writeOperationResult.getManifest().getStorageIds().size(), writeOperationResult.getInserts().size(), writeOperationResult.getDeletes().size()));
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
                StorageSyncHelper.applyAccountStorageSyncUpdates(context, mergeResult.getLocalAccountUpdate());
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

        List<StorageId>               allLocalStorageKeys  = getAllLocalStorageIds(context);
        List<RecipientSettings>       pendingUpdates       = recipientDatabase.getPendingRecipientSyncUpdates();
        List<RecipientSettings>       pendingInsertions    = recipientDatabase.getPendingRecipientSyncInsertions();
        List<RecipientSettings>       pendingDeletions     = recipientDatabase.getPendingRecipientSyncDeletions();
        Optional<SignalAccountRecord> pendingAccountUpdate = StorageSyncHelper.getPendingAccountSyncUpdate(context);
        Optional<SignalAccountRecord> pendingAccountInsert = StorageSyncHelper.getPendingAccountSyncInsert(context);
        Set<RecipientId>              archivedRecipients   = DatabaseFactory.getThreadDatabase(context).getArchivedRecipients();
        Optional<LocalWriteResult>    localWriteResult     = StorageSyncHelper.buildStorageUpdatesForLocal(localManifestVersion,
                allLocalStorageKeys,
                pendingUpdates,
                pendingInsertions,
                pendingDeletions,
                pendingAccountUpdate,
                pendingAccountInsert,
                archivedRecipients);

        if (localWriteResult.isPresent()) {
            Log.i(TAG, String.format(Locale.ENGLISH, "[Local Changes] Local changes present. %d updates, %d inserts, %d deletes, account update: %b, account insert %b.", pendingUpdates.size(), pendingInsertions.size(), pendingDeletions.size(), pendingAccountUpdate.isPresent(), pendingAccountInsert.isPresent()));

            WriteOperationResult localWrite = localWriteResult.get().getWriteResult();
            StorageSyncValidations.validate(localWrite);

            Log.i(TAG, "[Local Changes] WriteOperationResult :: " + localWrite);

            if (localWrite.isEmpty()) {
                throw new AssertionError("Decided there were local writes, but our write result was empty!");
            }
            Optional<SignalStorageManifest> conflict = accountManager.writeStorageRecords(storageServiceKey, localWrite.getManifest(), localWrite.getInserts(), localWrite.getDeletes());

            if (conflict.isPresent()) {
                Log.w(TAG, "[Local Changes] Hit a conflict when trying to upload our local writes! Retrying.");
                throw new RetryLaterException();
            }

            List<RecipientId> clearIds = new ArrayList<>(pendingUpdates.size() + pendingInsertions.size() + pendingDeletions.size() + 1);

            clearIds.addAll(Stream.of(pendingUpdates).map(RecipientSettings::getId).toList());
            clearIds.addAll(Stream.of(pendingInsertions).map(RecipientSettings::getId).toList());
            clearIds.addAll(Stream.of(pendingDeletions).map(RecipientSettings::getId).toList());
            clearIds.add(Recipient.self().getId());

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

    private static @NonNull List<StorageId> getAllLocalStorageIds(@NonNull Context context) {
        Recipient self = Recipient.self().fresh();

        return Util.concatenatedList(DatabaseFactory.getRecipientDatabase(context).getContactStorageSyncIds(),
                Collections.singletonList(StorageId.forAccount(self.getStorageServiceId())),
                DatabaseFactory.getStorageKeyDatabase(context).getAllKeys());
    }

    private static @NonNull List<SignalStorageRecord> buildLocalStorageRecords(@NonNull Context context, @NonNull List<StorageId> ids, @NonNull Set<RecipientId> archivedRecipients) {
        RecipientDatabase  recipientDatabase  = DatabaseFactory.getRecipientDatabase(context);
        StorageKeyDatabase storageKeyDatabase = DatabaseFactory.getStorageKeyDatabase(context);

        List<SignalStorageRecord> records = new ArrayList<>(ids.size());

        for (StorageId id : ids) {
            switch (id.getType()) {
                case ManifestRecord.Identifier.Type.CONTACT_VALUE:
                case ManifestRecord.Identifier.Type.GROUPV1_VALUE:
                case ManifestRecord.Identifier.Type.GROUPV2_VALUE:
                    RecipientSettings settings = recipientDatabase.getByStorageId(id.getRaw());
                    if (settings != null) {
                        records.add(StorageSyncModels.localToRemoteRecord(settings, archivedRecipients));
                    } else {
                        Log.w(TAG, "Missing local recipient model! Type: " + id.getType());
                    }
                    break;
                case ManifestRecord.Identifier.Type.ACCOUNT_VALUE:
                    records.add(StorageSyncHelper.buildAccountRecord(context, id));
                    break;
                default:
                    SignalStorageRecord unknown = storageKeyDatabase.getById(id.getRaw());
                    if (unknown != null) {
                        records.add(unknown);
                    } else {
                        Log.w(TAG, "Missing local unknown model! Type: " + id.getType());
                    }
                    break;
            }
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