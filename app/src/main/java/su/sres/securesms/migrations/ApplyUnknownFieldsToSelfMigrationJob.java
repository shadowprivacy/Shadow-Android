package su.sres.securesms.migrations;

import androidx.annotation.NonNull;

import com.google.protobuf.InvalidProtocolBufferException;

import su.sres.core.util.logging.Log;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.RecipientDatabase;
import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.storage.StorageSyncHelper;
import su.sres.securesms.util.TextSecurePreferences;
import su.sres.signalservice.api.storage.SignalAccountRecord;
import su.sres.signalservice.api.storage.StorageId;
import su.sres.signalservice.internal.storage.protos.AccountRecord;

/**
 * Check for unknown fields stored on self and attempt to apply them.
 */
public class ApplyUnknownFieldsToSelfMigrationJob extends MigrationJob {

  private static final String TAG = Log.tag(ApplyUnknownFieldsToSelfMigrationJob.class);

  public static final String KEY = "ApplyUnknownFieldsToSelfMigrationJob";

  ApplyUnknownFieldsToSelfMigrationJob() {
    this(new Parameters.Builder().build());
  }

  private ApplyUnknownFieldsToSelfMigrationJob(@NonNull Parameters parameters) {
    super(parameters);
  }

  @Override
  public boolean isUiBlocking() {
    return false;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void performMigration() {
    if (!TextSecurePreferences.isPushRegistered(context) || TextSecurePreferences.getLocalUuid(context) == null) {
      Log.w(TAG, "Not registered!");
      return;
    }

    Recipient                           self;
    RecipientDatabase.RecipientSettings settings;

    try {
      self     = Recipient.self();
      settings = DatabaseFactory.getRecipientDatabase(context).getRecipientSettingsForSync(self.getId());
    } catch (RecipientDatabase.MissingRecipientException e) {
      Log.w(TAG, "Unable to find self");
      return;
    }

    if (settings == null || settings.getSyncExtras().getStorageProto() == null) {
      Log.d(TAG, "No unknowns to apply");
      return;
    }

    try {
      StorageId           storageId           = StorageId.forAccount(self.getStorageServiceId());
      AccountRecord       accountRecord       = AccountRecord.parseFrom(settings.getSyncExtras().getStorageProto());
      SignalAccountRecord signalAccountRecord = new SignalAccountRecord(storageId, accountRecord);

      Log.d(TAG, "Applying potentially now known unknowns");
      StorageSyncHelper.applyAccountStorageSyncUpdates(context, self, signalAccountRecord, false);
    } catch (InvalidProtocolBufferException e) {
      Log.w(TAG, e);
    }
  }

  @Override
  boolean shouldRetry(@NonNull Exception e) {
    return false;
  }

  public static class Factory implements Job.Factory<ApplyUnknownFieldsToSelfMigrationJob> {
    @Override
    public @NonNull ApplyUnknownFieldsToSelfMigrationJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new ApplyUnknownFieldsToSelfMigrationJob(parameters);
    }
  }
}
