package su.sres.securesms.jobs;

import android.app.Application;

import androidx.annotation.NonNull;

import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.jobmanager.Constraint;
import su.sres.securesms.jobmanager.ConstraintObserver;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobmanager.JobMigration;
import su.sres.securesms.jobmanager.impl.AutoDownloadEmojiConstraint;
import su.sres.securesms.jobmanager.impl.ChargingConstraint;
import su.sres.securesms.jobmanager.impl.ChargingConstraintObserver;
import su.sres.securesms.jobmanager.impl.NetworkConstraint;
import su.sres.securesms.jobmanager.impl.NetworkConstraintObserver;
import su.sres.securesms.jobmanager.impl.NetworkOrCellServiceConstraint;
import su.sres.securesms.jobmanager.impl.NotInCallConstraint;
import su.sres.securesms.jobmanager.impl.NotInCallConstraintObserver;
import su.sres.securesms.jobmanager.impl.SqlCipherMigrationConstraint;
import su.sres.securesms.jobmanager.impl.SqlCipherMigrationConstraintObserver;
import su.sres.securesms.jobmanager.impl.DecryptionsDrainedConstraint;
import su.sres.securesms.jobmanager.impl.DecryptionsDrainedConstraintObserver;
import su.sres.securesms.jobmanager.migrations.PushDecryptMessageJobEnvelopeMigration;
import su.sres.securesms.jobmanager.migrations.PushProcessMessageQueueJobMigration;
import su.sres.securesms.jobmanager.migrations.RecipientIdFollowUpJobMigration;
import su.sres.securesms.jobmanager.migrations.RecipientIdFollowUpJobMigration2;
import su.sres.securesms.jobmanager.migrations.RecipientIdJobMigration;
import su.sres.securesms.jobmanager.migrations.RetrieveProfileJobMigration;
import su.sres.securesms.jobmanager.migrations.SendReadReceiptsJobMigration;
import su.sres.securesms.migrations.AccountRecordMigrationJob;
import su.sres.securesms.migrations.ApplyUnknownFieldsToSelfMigrationJob;
import su.sres.securesms.migrations.AttachmentCleanupMigrationJob;
import su.sres.securesms.migrations.AttributesMigrationJob;
import su.sres.securesms.migrations.AvatarIdRemovalMigrationJob;
import su.sres.securesms.migrations.BackupNotificationMigrationJob;
import su.sres.securesms.migrations.BlobStorageLocationMigrationJob;
import su.sres.securesms.migrations.DeleteDeprecatedLogsMigrationJob;
import su.sres.securesms.migrations.DirectoryRefreshMigrationJob;
import su.sres.securesms.migrations.LicenseMigrationJob;
import su.sres.securesms.migrations.PassingMigrationJob;
import su.sres.securesms.migrations.CachedAttachmentsMigrationJob;
import su.sres.securesms.migrations.DatabaseMigrationJob;
import su.sres.securesms.migrations.MigrationCompleteJob;
import su.sres.securesms.migrations.ProfileMigrationJob;
import su.sres.securesms.migrations.ProfileSharingUpdateMigrationJob;
import su.sres.securesms.migrations.RecipientSearchMigrationJob;
import su.sres.securesms.migrations.SfuCertJob;
import su.sres.securesms.migrations.StickerDayByDayMigrationJob;
import su.sres.securesms.migrations.StickerLaunchMigrationJob;
import su.sres.securesms.migrations.StickerMyDailyLifeMigrationJob;
import su.sres.securesms.migrations.StorageCapabilityMigrationJob;
import su.sres.securesms.migrations.StorageServiceMigrationJob;
import su.sres.securesms.migrations.StickerAdditionMigrationJob;
import su.sres.securesms.migrations.TrimByLengthSettingsMigrationJob;
import su.sres.securesms.migrations.UuidMigrationJob;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class JobManagerFactories {

  public static Map<String, Job.Factory> getJobFactories(@NonNull Application application) {
    return new HashMap<String, Job.Factory>() {{
      put(AttachmentCopyJob.KEY, new AttachmentCopyJob.Factory());
      put(AttachmentDownloadJob.KEY, new AttachmentDownloadJob.Factory());
      put(AttachmentUploadJob.KEY, new AttachmentUploadJob.Factory());
      put(AttachmentMarkUploadedJob.KEY, new AttachmentMarkUploadedJob.Factory());
      put(AttachmentCompressionJob.KEY, new AttachmentCompressionJob.Factory());
      put(AutomaticSessionResetJob.KEY, new AutomaticSessionResetJob.Factory());
      put(AvatarGroupsV1DownloadJob.KEY, new AvatarGroupsV1DownloadJob.Factory());
      put(AvatarGroupsV2DownloadJob.KEY, new AvatarGroupsV2DownloadJob.Factory());
      put(CertificatePullJob.KEY, new CertificatePullJob.Factory());
      put(CertificateRefreshJob.KEY, new CertificateRefreshJob.Factory());
      put(CleanPreKeysJob.KEY, new CleanPreKeysJob.Factory());
      put(ConversationShortcutUpdateJob.KEY, new ConversationShortcutUpdateJob.Factory());
      put(CreateSignedPreKeyJob.KEY, new CreateSignedPreKeyJob.Factory());
      put(DirectorySyncJob.KEY, new DirectorySyncJob.Factory());
      put(DownloadLatestEmojiDataJob.KEY, new DownloadLatestEmojiDataJob.Factory());
      put(EmojiSearchIndexDownloadJob.KEY, new EmojiSearchIndexDownloadJob.Factory());
      put(FcmRefreshJob.KEY, new FcmRefreshJob.Factory());
      put(GroupV1MigrationJob.KEY, new GroupV1MigrationJob.Factory());
      put(GroupCallUpdateSendJob.KEY, new GroupCallUpdateSendJob.Factory());
      put(GroupCallPeekJob.KEY, new GroupCallPeekJob.Factory());
      put(GroupCallPeekWorkerJob.KEY, new GroupCallPeekWorkerJob.Factory());
      put(GroupV2UpdateSelfProfileKeyJob.KEY, new GroupV2UpdateSelfProfileKeyJob.Factory());
      put(LicenseManagementJob.KEY, new LicenseManagementJob.Factory());
      put(LocalBackupJob.KEY, new LocalBackupJob.Factory());
      put(LocalBackupJobApi29.KEY, new LocalBackupJobApi29.Factory());
      put(MarkerJob.KEY, new MarkerJob.Factory());
      put(MmsDownloadJob.KEY, new MmsDownloadJob.Factory());
      put(MmsReceiveJob.KEY, new MmsReceiveJob.Factory());
      put(MmsSendJob.KEY, new MmsSendJob.Factory());
      put(MultiDeviceBlockedUpdateJob.KEY, new MultiDeviceBlockedUpdateJob.Factory());
      put(MultiDeviceConfigurationUpdateJob.KEY, new MultiDeviceConfigurationUpdateJob.Factory());
      put(MultiDeviceContactUpdateJob.KEY, new MultiDeviceContactUpdateJob.Factory());
      put(MultiDeviceGroupUpdateJob.KEY, new MultiDeviceGroupUpdateJob.Factory());
      put(MultiDeviceKeysUpdateJob.KEY, new MultiDeviceKeysUpdateJob.Factory());
      put(MultiDeviceMessageRequestResponseJob.KEY, new MultiDeviceMessageRequestResponseJob.Factory());
      put(MultiDeviceOutgoingPaymentSyncJob.KEY, new MultiDeviceOutgoingPaymentSyncJob.Factory());
      put(MultiDeviceProfileContentUpdateJob.KEY, new MultiDeviceProfileContentUpdateJob.Factory());
      put(MultiDeviceProfileKeyUpdateJob.KEY, new MultiDeviceProfileKeyUpdateJob.Factory());
      put(MultiDeviceReadUpdateJob.KEY, new MultiDeviceReadUpdateJob.Factory());
      put(MultiDeviceStickerPackOperationJob.KEY, new MultiDeviceStickerPackOperationJob.Factory());
      put(MultiDeviceStickerPackSyncJob.KEY, new MultiDeviceStickerPackSyncJob.Factory());
      put(MultiDeviceStorageSyncRequestJob.KEY, new MultiDeviceStorageSyncRequestJob.Factory());
      put(MultiDeviceVerifiedUpdateJob.KEY, new MultiDeviceVerifiedUpdateJob.Factory());
      put(MultiDeviceViewOnceOpenJob.KEY, new MultiDeviceViewOnceOpenJob.Factory());
      put(MultiDeviceViewedUpdateJob.KEY, new MultiDeviceViewedUpdateJob.Factory());
      put(NullMessageSendJob.KEY, new NullMessageSendJob.Factory());
      put(PaymentLedgerUpdateJob.KEY, new PaymentLedgerUpdateJob.Factory());
      put(PaymentNotificationSendJob.KEY, new PaymentNotificationSendJob.Factory());
      put(PaymentSendJob.KEY, new PaymentSendJob.Factory());
      put(PaymentTransactionCheckJob.KEY, new PaymentTransactionCheckJob.Factory());
      put(ProfileKeySendJob.KEY, new ProfileKeySendJob.Factory());
      put(ProfileUploadJob.KEY, new ProfileUploadJob.Factory());
      put(PushDecryptMessageJob.KEY, new PushDecryptMessageJob.Factory());
      put(PushDecryptDrainedJob.KEY, new PushDecryptDrainedJob.Factory());
      put(PushProcessMessageJob.KEY, new PushProcessMessageJob.Factory());
      put(PushGroupSendJob.KEY, new PushGroupSendJob.Factory());
      put(PushGroupSilentUpdateSendJob.KEY, new PushGroupSilentUpdateSendJob.Factory());
      put(PushGroupUpdateJob.KEY, new PushGroupUpdateJob.Factory());
      put(PushMediaSendJob.KEY, new PushMediaSendJob.Factory());
      put(PushNotificationReceiveJob.KEY, new PushNotificationReceiveJob.Factory());
      put(PushTextSendJob.KEY, new PushTextSendJob.Factory());
      put(ReactionSendJob.KEY, new ReactionSendJob.Factory());
      put(RecipientChangedLoginJob.KEY, new RecipientChangedLoginJob.Factory());
      put(RefreshAttributesJob.KEY, new RefreshAttributesJob.Factory());
      put(RefreshOwnProfileJob.KEY, new RefreshOwnProfileJob.Factory());
      put(RefreshPreKeysJob.KEY, new RefreshPreKeysJob.Factory());
      put(RemoteConfigRefreshJob.KEY, new RemoteConfigRefreshJob.Factory());
      put(RemoteDeleteSendJob.KEY, new RemoteDeleteSendJob.Factory());
      put(ReportSpamJob.KEY, new ReportSpamJob.Factory());
      put(RequestGroupInfoJob.KEY, new RequestGroupInfoJob.Factory());
      put(ResendMessageJob.KEY, new ResendMessageJob.Factory());
      put(ResumableUploadSpecJob.KEY, new ResumableUploadSpecJob.Factory());
      put(ServiceConfigRefreshJob.KEY, new ServiceConfigRefreshJob.Factory());
      put(RequestGroupV2InfoWorkerJob.KEY, new RequestGroupV2InfoWorkerJob.Factory());
      put(RequestGroupV2InfoJob.KEY, new RequestGroupV2InfoJob.Factory());
      put(RetrieveProfileAvatarJob.KEY, new RetrieveProfileAvatarJob.Factory());
      put(RetrieveProfileJob.KEY, new RetrieveProfileJob.Factory());
      put(RotateCertificateJob.KEY, new RotateCertificateJob.Factory());
      put(RotateProfileKeyJob.KEY, new RotateProfileKeyJob.Factory());
      put(RotateSignedPreKeyJob.KEY, new RotateSignedPreKeyJob.Factory());
      put(SenderKeyDistributionSendJob.KEY, new SenderKeyDistributionSendJob.Factory());
      put(SendDeliveryReceiptJob.KEY, new SendDeliveryReceiptJob.Factory());
      put(SendReadReceiptJob.KEY, new SendReadReceiptJob.Factory(application));
      put(SendRetryReceiptJob.KEY, new SendRetryReceiptJob.Factory());
      put(SendViewedReceiptJob.KEY, new SendViewedReceiptJob.Factory(application));
      put(ServiceOutageDetectionJob.KEY, new ServiceOutageDetectionJob.Factory());
      put(SmsReceiveJob.KEY, new SmsReceiveJob.Factory());
      put(SmsSendJob.KEY, new SmsSendJob.Factory());
      put(SmsSentJob.KEY, new SmsSentJob.Factory());
      put(StickerDownloadJob.KEY, new StickerDownloadJob.Factory());
      put(StickerPackDownloadJob.KEY, new StickerPackDownloadJob.Factory());
      put(StorageAccountRestoreJob.KEY, new StorageAccountRestoreJob.Factory());
      put(StorageForcePushJob.KEY, new StorageForcePushJob.Factory());
      put(StorageSyncJob.KEY, new StorageSyncJob.Factory());
      put(SubmitRateLimitPushChallengeJob.KEY, new SubmitRateLimitPushChallengeJob.Factory());
      put(ThreadUpdateJob.KEY, new ThreadUpdateJob.Factory());
      put(TrimThreadJob.KEY, new TrimThreadJob.Factory());
      put(TypingSendJob.KEY, new TypingSendJob.Factory());
      put(UpdateApkJob.KEY, new UpdateApkJob.Factory());

      // Migrations
      put(AccountRecordMigrationJob.KEY, new AccountRecordMigrationJob.Factory());
      put(ApplyUnknownFieldsToSelfMigrationJob.KEY, new ApplyUnknownFieldsToSelfMigrationJob.Factory());
      put(AttachmentCleanupMigrationJob.KEY, new AttachmentCleanupMigrationJob.Factory());
      put(AttributesMigrationJob.KEY, new AttributesMigrationJob.Factory());
      put(AvatarIdRemovalMigrationJob.KEY, new AvatarIdRemovalMigrationJob.Factory());
      put(BackupNotificationMigrationJob.KEY, new BackupNotificationMigrationJob.Factory());
      put(BlobStorageLocationMigrationJob.KEY, new BlobStorageLocationMigrationJob.Factory());
      put(CachedAttachmentsMigrationJob.KEY, new CachedAttachmentsMigrationJob.Factory());
      put(DatabaseMigrationJob.KEY, new DatabaseMigrationJob.Factory());
      put(DeleteDeprecatedLogsMigrationJob.KEY, new DeleteDeprecatedLogsMigrationJob.Factory());
      put(DirectoryRefreshMigrationJob.KEY, new DirectoryRefreshMigrationJob.Factory());
      put(LicenseMigrationJob.KEY, new LicenseMigrationJob.Factory());
      put(MigrationCompleteJob.KEY, new MigrationCompleteJob.Factory());
      put(ProfileMigrationJob.KEY, new ProfileMigrationJob.Factory());
      put(ProfileSharingUpdateMigrationJob.KEY, new ProfileSharingUpdateMigrationJob.Factory());
      put(RecipientSearchMigrationJob.KEY, new RecipientSearchMigrationJob.Factory());
      put(SfuCertJob.KEY, new SfuCertJob.Factory());
      put(StickerLaunchMigrationJob.KEY, new StickerLaunchMigrationJob.Factory());
      put(StickerAdditionMigrationJob.KEY, new StickerAdditionMigrationJob.Factory());
      put(StickerDayByDayMigrationJob.KEY, new StickerDayByDayMigrationJob.Factory());
      put(StickerMyDailyLifeMigrationJob.KEY, new StickerMyDailyLifeMigrationJob.Factory());
      put(StorageCapabilityMigrationJob.KEY, new StorageCapabilityMigrationJob.Factory());
      put(StorageServiceMigrationJob.KEY, new StorageServiceMigrationJob.Factory());
      put(TrimByLengthSettingsMigrationJob.KEY, new TrimByLengthSettingsMigrationJob.Factory());
      put(UuidMigrationJob.KEY, new UuidMigrationJob.Factory());

      // Dead jobs
      put(FailingJob.KEY, new FailingJob.Factory());
      put(PassingMigrationJob.KEY, new PassingMigrationJob.Factory());
      put("PushContentReceiveJob", new FailingJob.Factory());
      put("AttachmentUploadJob", new FailingJob.Factory());
      put("MmsSendJob", new FailingJob.Factory());
      put("RefreshUnidentifiedDeliveryAbilityJob", new FailingJob.Factory());
      put("StorageSyncJob", new StorageSyncJob.Factory());
      put("WakeGroupV2Job", new FailingJob.Factory());
      put("LeaveGroupJob", new FailingJob.Factory());
    }};
  }

  public static Map<String, Constraint.Factory> getConstraintFactories(@NonNull Application application) {
    return new HashMap<String, Constraint.Factory>() {{
      put(AutoDownloadEmojiConstraint.KEY, new AutoDownloadEmojiConstraint.Factory(application));
      put(ChargingConstraint.KEY, new ChargingConstraint.Factory());
      put(NetworkConstraint.KEY, new NetworkConstraint.Factory(application));
      put(NetworkOrCellServiceConstraint.KEY, new NetworkOrCellServiceConstraint.Factory(application));
      put(NetworkOrCellServiceConstraint.LEGACY_KEY, new NetworkOrCellServiceConstraint.Factory(application));
      put(SqlCipherMigrationConstraint.KEY, new SqlCipherMigrationConstraint.Factory(application));
      put(DecryptionsDrainedConstraint.KEY, new DecryptionsDrainedConstraint.Factory());
      put(NotInCallConstraint.KEY, new NotInCallConstraint.Factory());
    }};
  }

  public static List<ConstraintObserver> getConstraintObservers(@NonNull Application application) {
    return Arrays.asList(new ChargingConstraintObserver(application),
                         new NetworkConstraintObserver(application),
                         new SqlCipherMigrationConstraintObserver(),
                         new DecryptionsDrainedConstraintObserver(),
                         new NotInCallConstraintObserver());
  }

  public static List<JobMigration> getJobMigrations(@NonNull Application application) {
    return Arrays.asList(new RecipientIdJobMigration(application),
                         new RecipientIdFollowUpJobMigration(),
                         new RecipientIdFollowUpJobMigration2(),
                         new SendReadReceiptsJobMigration(DatabaseFactory.getMmsSmsDatabase(application)),
                         new PushProcessMessageQueueJobMigration(application),
                         new RetrieveProfileJobMigration(),
                         new PushDecryptMessageJobEnvelopeMigration(application));
  }
}