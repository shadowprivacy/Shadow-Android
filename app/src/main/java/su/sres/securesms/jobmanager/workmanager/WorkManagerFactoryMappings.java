package su.sres.securesms.jobmanager.workmanager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import su.sres.securesms.jobs.AttachmentDownloadJob;
import su.sres.securesms.jobs.AttachmentUploadJob;
import su.sres.securesms.jobs.AvatarGroupsV1DownloadJob;
import su.sres.securesms.jobs.CleanPreKeysJob;
import su.sres.securesms.jobs.CreateSignedPreKeyJob;
import su.sres.securesms.jobs.DirectorySyncJob;
import su.sres.securesms.jobs.FailingJob;
import su.sres.securesms.jobs.FcmRefreshJob;
import su.sres.securesms.jobs.LocalBackupJob;
import su.sres.securesms.jobs.MmsDownloadJob;
import su.sres.securesms.jobs.MmsReceiveJob;
import su.sres.securesms.jobs.MmsSendJob;
import su.sres.securesms.jobs.MultiDeviceBlockedUpdateJob;
import su.sres.securesms.jobs.MultiDeviceConfigurationUpdateJob;
import su.sres.securesms.jobs.MultiDeviceContactUpdateJob;
import su.sres.securesms.jobs.MultiDeviceGroupUpdateJob;
import su.sres.securesms.jobs.MultiDeviceProfileKeyUpdateJob;
import su.sres.securesms.jobs.MultiDeviceReadUpdateJob;
import su.sres.securesms.jobs.MultiDeviceVerifiedUpdateJob;
import su.sres.securesms.jobs.PushDecryptMessageJob;
import su.sres.securesms.jobs.PushGroupSendJob;
import su.sres.securesms.jobs.PushGroupUpdateJob;
import su.sres.securesms.jobs.PushMediaSendJob;
import su.sres.securesms.jobs.PushNotificationReceiveJob;
import su.sres.securesms.jobs.PushTextSendJob;
import su.sres.securesms.jobs.RefreshAttributesJob;
import su.sres.securesms.jobs.RefreshPreKeysJob;
import su.sres.securesms.jobs.RequestGroupInfoJob;
import su.sres.securesms.jobs.RetrieveProfileAvatarJob;
import su.sres.securesms.jobs.RetrieveProfileJob;
import su.sres.securesms.jobs.RotateCertificateJob;
import su.sres.securesms.jobs.RotateProfileKeyJob;
import su.sres.securesms.jobs.RotateSignedPreKeyJob;
import su.sres.securesms.jobs.SendDeliveryReceiptJob;
import su.sres.securesms.jobs.SendReadReceiptJob;
import su.sres.securesms.jobs.ServiceOutageDetectionJob;
import su.sres.securesms.jobs.SmsReceiveJob;
import su.sres.securesms.jobs.SmsSendJob;
import su.sres.securesms.jobs.SmsSentJob;
import su.sres.securesms.jobs.TrimThreadJob;
import su.sres.securesms.jobs.TypingSendJob;
import su.sres.securesms.jobs.UpdateApkJob;

import java.util.HashMap;
import java.util.Map;

public class WorkManagerFactoryMappings {

    private static final Map<String, String> FACTORY_MAP = new HashMap<String, String>() {{
        put("AttachmentDownloadJob", AttachmentDownloadJob.KEY);
        put("AttachmentUploadJob", AttachmentUploadJob.KEY);
        put("AvatarDownloadJob", AvatarGroupsV1DownloadJob.KEY);
        put("CleanPreKeysJob", CleanPreKeysJob.KEY);
        put("CreateSignedPreKeyJob", CreateSignedPreKeyJob.KEY);
     //   put("DirectoryRefreshJob", DirectoryRefreshJob.KEY);
        put("DirectorySyncJob", DirectorySyncJob.KEY);
        put("FcmRefreshJob", FcmRefreshJob.KEY);
        put("LocalBackupJob", LocalBackupJob.KEY);
        put("MmsDownloadJob", MmsDownloadJob.KEY);
        put("MmsReceiveJob", MmsReceiveJob.KEY);
        put("MmsSendJob", MmsSendJob.KEY);
        put("MultiDeviceBlockedUpdateJob", MultiDeviceBlockedUpdateJob.KEY);
        put("MultiDeviceConfigurationUpdateJob", MultiDeviceConfigurationUpdateJob.KEY);
        put("MultiDeviceContactUpdateJob", MultiDeviceContactUpdateJob.KEY);
        put("MultiDeviceGroupUpdateJob", MultiDeviceGroupUpdateJob.KEY);
        put("MultiDeviceProfileKeyUpdateJob", MultiDeviceProfileKeyUpdateJob.KEY);
        put("MultiDeviceReadUpdateJob", MultiDeviceReadUpdateJob.KEY);
        put("MultiDeviceVerifiedUpdateJob", MultiDeviceVerifiedUpdateJob.KEY);
        put("PushContentReceiveJob", FailingJob.KEY);
        put("PushDecryptJob", PushDecryptMessageJob.KEY);
        put("PushGroupSendJob", PushGroupSendJob.KEY);
        put("PushGroupUpdateJob", PushGroupUpdateJob.KEY);
        put("PushMediaSendJob", PushMediaSendJob.KEY);
        put("PushNotificationReceiveJob", PushNotificationReceiveJob.KEY);
        put("PushTextSendJob", PushTextSendJob.KEY);
        put("RefreshAttributesJob", RefreshAttributesJob.KEY);
        put("RefreshPreKeysJob", RefreshPreKeysJob.KEY);
        put("RefreshUnidentifiedDeliveryAbilityJob", FailingJob.KEY);
        put("RequestGroupInfoJob", RequestGroupInfoJob.KEY);
        put("RetrieveProfileAvatarJob", RetrieveProfileAvatarJob.KEY);
        put("RetrieveProfileJob", RetrieveProfileJob.KEY);
        put("RotateCertificateJob", RotateCertificateJob.KEY);
        put("RotateProfileKeyJob", RotateProfileKeyJob.KEY);
        put("RotateSignedPreKeyJob", RotateSignedPreKeyJob.KEY);
        put("SendDeliveryReceiptJob", SendDeliveryReceiptJob.KEY);
        put("SendReadReceiptJob", SendReadReceiptJob.KEY);
        put("ServiceOutageDetectionJob", ServiceOutageDetectionJob.KEY);
        put("SmsReceiveJob", SmsReceiveJob.KEY);
        put("SmsSendJob", SmsSendJob.KEY);
        put("SmsSentJob", SmsSentJob.KEY);
        put("TrimThreadJob", TrimThreadJob.KEY);
        put("TypingSendJob", TypingSendJob.KEY);
        put("UpdateApkJob", UpdateApkJob.KEY);
    }};

    public static @Nullable String getFactoryKey(@NonNull String workManagerClass) {
        return FACTORY_MAP.get(workManagerClass);
    }
}