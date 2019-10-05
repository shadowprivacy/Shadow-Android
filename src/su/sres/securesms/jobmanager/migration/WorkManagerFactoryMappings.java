package su.sres.securesms.jobmanager.migration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import su.sres.securesms.jobs.AttachmentDownloadJob;
import su.sres.securesms.jobs.AttachmentUploadJob;
import su.sres.securesms.jobs.AvatarDownloadJob;
import su.sres.securesms.jobs.CleanPreKeysJob;
import su.sres.securesms.jobs.CreateSignedPreKeyJob;
import su.sres.securesms.jobs.DirectoryRefreshJob;
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
import su.sres.securesms.jobs.PushDecryptJob;
import su.sres.securesms.jobs.PushGroupSendJob;
import su.sres.securesms.jobs.PushGroupUpdateJob;
import su.sres.securesms.jobs.PushMediaSendJob;
import su.sres.securesms.jobs.PushNotificationReceiveJob;
import su.sres.securesms.jobs.PushTextSendJob;
import su.sres.securesms.jobs.RefreshAttributesJob;
import su.sres.securesms.jobs.RefreshPreKeysJob;
import su.sres.securesms.jobs.RefreshUnidentifiedDeliveryAbilityJob;
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
        put(AttachmentDownloadJob.class.getName(), AttachmentDownloadJob.KEY);
        put(AttachmentUploadJob.class.getName(), AttachmentUploadJob.KEY);
        put(AvatarDownloadJob.class.getName(), AvatarDownloadJob.KEY);
        put(CleanPreKeysJob.class.getName(), CleanPreKeysJob.KEY);
        put(CreateSignedPreKeyJob.class.getName(), CreateSignedPreKeyJob.KEY);
        put(DirectoryRefreshJob.class.getName(), DirectoryRefreshJob.KEY);
        put(FcmRefreshJob.class.getName(), FcmRefreshJob.KEY);
        put(LocalBackupJob.class.getName(), LocalBackupJob.KEY);
        put(MmsDownloadJob.class.getName(), MmsDownloadJob.KEY);
        put(MmsReceiveJob.class.getName(), MmsReceiveJob.KEY);
        put(MmsSendJob.class.getName(), MmsSendJob.KEY);
        put(MultiDeviceBlockedUpdateJob.class.getName(), MultiDeviceBlockedUpdateJob.KEY);
        put(MultiDeviceConfigurationUpdateJob.class.getName(), MultiDeviceConfigurationUpdateJob.KEY);
        put(MultiDeviceContactUpdateJob.class.getName(), MultiDeviceContactUpdateJob.KEY);
        put(MultiDeviceGroupUpdateJob.class.getName(), MultiDeviceGroupUpdateJob.KEY);
        put(MultiDeviceProfileKeyUpdateJob.class.getName(), MultiDeviceProfileKeyUpdateJob.KEY);
        put(MultiDeviceReadUpdateJob.class.getName(), MultiDeviceReadUpdateJob.KEY);
        put(MultiDeviceVerifiedUpdateJob.class.getName(), MultiDeviceVerifiedUpdateJob.KEY);
        put("PushContentReceiveJob", FailingJob.KEY);
        put(PushDecryptJob.class.getName(), PushDecryptJob.KEY);
        put(PushGroupSendJob.class.getName(), PushGroupSendJob.KEY);
        put(PushGroupUpdateJob.class.getName(), PushGroupUpdateJob.KEY);
        put(PushMediaSendJob.class.getName(), PushMediaSendJob.KEY);
        put(PushNotificationReceiveJob.class.getName(), PushNotificationReceiveJob.KEY);
        put(PushTextSendJob.class.getName(), PushTextSendJob.KEY);
        put(RefreshAttributesJob.class.getName(), RefreshAttributesJob.KEY);
        put(RefreshPreKeysJob.class.getName(), RefreshPreKeysJob.KEY);
        put(RefreshUnidentifiedDeliveryAbilityJob.class.getName(), RefreshUnidentifiedDeliveryAbilityJob.KEY);
        put(RequestGroupInfoJob.class.getName(), RequestGroupInfoJob.KEY);
        put(RetrieveProfileAvatarJob.class.getName(), RetrieveProfileAvatarJob.KEY);
        put(RetrieveProfileJob.class.getName(), RetrieveProfileJob.KEY);
        put(RotateCertificateJob.class.getName(), RotateCertificateJob.KEY);
        put(RotateProfileKeyJob.class.getName(), RotateProfileKeyJob.KEY);
        put(RotateSignedPreKeyJob.class.getName(), RotateSignedPreKeyJob.KEY);
        put(SendDeliveryReceiptJob.class.getName(), SendDeliveryReceiptJob.KEY);
        put(SendReadReceiptJob.class.getName(), SendReadReceiptJob.KEY);
        put(ServiceOutageDetectionJob.class.getName(), ServiceOutageDetectionJob.KEY);
        put(SmsReceiveJob.class.getName(), SmsReceiveJob.KEY);
        put(SmsSendJob.class.getName(), SmsSendJob.KEY);
        put(SmsSentJob.class.getName(), SmsSentJob.KEY);
        put(TrimThreadJob.class.getName(), TrimThreadJob.KEY);
        put(TypingSendJob.class.getName(), TypingSendJob.KEY);
        put(UpdateApkJob.class.getName(), UpdateApkJob.KEY);
    }};

    public static @Nullable String getFactoryKey(@NonNull String workManagerClass) {
        return FACTORY_MAP.get(workManagerClass);
    }
}