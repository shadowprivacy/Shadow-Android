package su.sres.securesms.dependencies;

import android.content.Context;

import su.sres.securesms.gcm.FcmService;
import su.sres.securesms.jobs.AttachmentUploadJob;
import su.sres.securesms.jobs.MultiDeviceConfigurationUpdateJob;
import su.sres.securesms.jobs.RefreshUnidentifiedDeliveryAbilityJob;
import su.sres.securesms.jobs.RotateProfileKeyJob;
import su.sres.securesms.jobs.TypingSendJob;
import su.sres.securesms.logging.Log;

import org.greenrobot.eventbus.EventBus;
import su.sres.securesms.BuildConfig;
import su.sres.securesms.CreateProfileActivity;
import su.sres.securesms.DeviceListFragment;
import su.sres.securesms.crypto.storage.SignalProtocolStoreImpl;
import su.sres.securesms.events.ReminderUpdateEvent;
import su.sres.securesms.jobs.AttachmentDownloadJob;
import su.sres.securesms.jobs.AvatarDownloadJob;
import su.sres.securesms.jobs.CleanPreKeysJob;
import su.sres.securesms.jobs.CreateSignedPreKeyJob;
import su.sres.securesms.jobs.FcmRefreshJob;
import su.sres.securesms.jobs.MultiDeviceBlockedUpdateJob;
import su.sres.securesms.jobs.MultiDeviceContactUpdateJob;
import su.sres.securesms.jobs.MultiDeviceGroupUpdateJob;
import su.sres.securesms.jobs.MultiDeviceProfileKeyUpdateJob;
import su.sres.securesms.jobs.MultiDeviceReadUpdateJob;
import su.sres.securesms.jobs.MultiDeviceStickerPackOperationJob;
import su.sres.securesms.jobs.MultiDeviceStickerPackSyncJob;
import su.sres.securesms.jobs.MultiDeviceVerifiedUpdateJob;
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
import su.sres.securesms.jobs.RotateSignedPreKeyJob;
import su.sres.securesms.jobs.SendDeliveryReceiptJob;
import su.sres.securesms.jobs.SendReadReceiptJob;
import su.sres.securesms.jobs.StickerDownloadJob;
import su.sres.securesms.jobs.StickerPackDownloadJob;
import su.sres.securesms.linkpreview.LinkPreviewRepository;
import su.sres.securesms.preferences.AppProtectionPreferenceFragment;
import su.sres.securesms.push.SecurityEventListener;
import su.sres.securesms.push.SignalServiceNetworkAccess;
import su.sres.securesms.service.IncomingMessageObserver;
import su.sres.securesms.service.WebRtcCallService;
import su.sres.securesms.stickers.StickerPackPreviewRepository;
import su.sres.securesms.stickers.StickerRemoteUriLoader;
import su.sres.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.util.guava.Optional;
import su.sres.signalservice.api.SignalServiceAccountManager;
import su.sres.signalservice.api.SignalServiceMessageReceiver;
import su.sres.signalservice.api.SignalServiceMessageSender;
import su.sres.signalservice.api.util.CredentialsProvider;
import su.sres.signalservice.api.util.RealtimeSleepTimer;
import su.sres.signalservice.api.util.SleepTimer;
import su.sres.signalservice.api.util.UptimeSleepTimer;
import su.sres.signalservice.api.websocket.ConnectivityListener;

import dagger.Module;
import dagger.Provides;

@Module(complete = false, injects = {CleanPreKeysJob.class,
                                     CreateSignedPreKeyJob.class,
                                     PushGroupSendJob.class,
                                     PushTextSendJob.class,
                                     PushMediaSendJob.class,
                                     AttachmentDownloadJob.class,
                                     RefreshPreKeysJob.class,
                                     IncomingMessageObserver.class,
                                     PushNotificationReceiveJob.class,
                                     MultiDeviceContactUpdateJob.class,
                                     MultiDeviceGroupUpdateJob.class,
                                     MultiDeviceReadUpdateJob.class,
                                     MultiDeviceBlockedUpdateJob.class,
                                     DeviceListFragment.class,
                                     RefreshAttributesJob.class,
                                     FcmRefreshJob.class,
                                     RequestGroupInfoJob.class,
                                     PushGroupUpdateJob.class,
                                     AvatarDownloadJob.class,
                                     RotateSignedPreKeyJob.class,
                                     WebRtcCallService.class,
                                     RetrieveProfileJob.class,
                                     MultiDeviceVerifiedUpdateJob.class,
                                     CreateProfileActivity.class,
                                     RetrieveProfileAvatarJob.class,
                                     MultiDeviceProfileKeyUpdateJob.class,
                                     SendReadReceiptJob.class,
                                     AppProtectionPreferenceFragment.class,
                                     FcmService.class,
                                     RotateCertificateJob.class,
                                     SendDeliveryReceiptJob.class,
                                     RotateProfileKeyJob.class,
                                     MultiDeviceConfigurationUpdateJob.class,
                                     RefreshUnidentifiedDeliveryAbilityJob.class,
        TypingSendJob.class,
        AttachmentUploadJob.class,
        StickerDownloadJob.class,
        StickerPackPreviewRepository.class,
        StickerRemoteUriLoader.Factory.class,
        StickerPackDownloadJob.class,
        MultiDeviceStickerPackOperationJob.class,
        MultiDeviceStickerPackSyncJob.class,
        LinkPreviewRepository.class})
public class SignalCommunicationModule {

  private static final String TAG = SignalCommunicationModule.class.getSimpleName();

  private final Context                      context;
  private final SignalServiceNetworkAccess   networkAccess;

  private SignalServiceAccountManager  accountManager;
  private SignalServiceMessageSender   messageSender;
  private SignalServiceMessageReceiver messageReceiver;

  public SignalCommunicationModule(Context context, SignalServiceNetworkAccess networkAccess) {
    this.context       = context;
    this.networkAccess = networkAccess;
  }

  @Provides
  synchronized SignalServiceAccountManager provideSignalAccountManager() {
    if (this.accountManager == null) {
      this.accountManager = new SignalServiceAccountManager(networkAccess.getConfiguration(context),
                                                            new DynamicCredentialsProvider(context),
                                                            BuildConfig.USER_AGENT);
    }

    return this.accountManager;
  }

  @Provides
  synchronized SignalServiceMessageSender provideSignalMessageSender() {
    if (this.messageSender == null) {
      this.messageSender = new SignalServiceMessageSender(networkAccess.getConfiguration(context),
                                                          new DynamicCredentialsProvider(context),
                                                          new SignalProtocolStoreImpl(context),
                                                          BuildConfig.USER_AGENT,
                                                          TextSecurePreferences.isMultiDevice(context),
                                                          Optional.fromNullable(IncomingMessageObserver.getPipe()),
                                                          Optional.fromNullable(IncomingMessageObserver.getUnidentifiedPipe()),
                                                          Optional.of(new SecurityEventListener(context)));
    } else {
      this.messageSender.setMessagePipe(IncomingMessageObserver.getPipe(), IncomingMessageObserver.getUnidentifiedPipe());
      this.messageSender.setIsMultiDevice(TextSecurePreferences.isMultiDevice(context));
    }

    return this.messageSender;
  }

  @Provides
  synchronized SignalServiceMessageReceiver provideSignalMessageReceiver() {
    if (this.messageReceiver == null) {
      SleepTimer sleepTimer =  TextSecurePreferences.isFcmDisabled(context) ? new RealtimeSleepTimer(context) : new UptimeSleepTimer();


      this.messageReceiver = new SignalServiceMessageReceiver(networkAccess.getConfiguration(context),
                                                              new DynamicCredentialsProvider(context),
                                                              BuildConfig.USER_AGENT,
              new PipeConnectivityListener(),
              sleepTimer);
    }

    return this.messageReceiver;
  }

  @Provides
  synchronized SignalServiceNetworkAccess provideSignalServiceNetworkAccess() {
    return networkAccess;
  }

  private static class DynamicCredentialsProvider implements CredentialsProvider {

    private final Context context;

    private DynamicCredentialsProvider(Context context) {
      this.context = context.getApplicationContext();
    }

    @Override
    public String getUser() {
      return TextSecurePreferences.getLocalNumber(context);
    }

    @Override
    public String getPassword() {
      return TextSecurePreferences.getPushServerPassword(context);
    }

    @Override
    public String getSignalingKey() {
      return TextSecurePreferences.getSignalingKey(context);
    }
  }

  private class PipeConnectivityListener implements ConnectivityListener {

    @Override
    public void onConnected() {
      Log.i(TAG, "onConnected()");
    }

    @Override
    public void onConnecting() {
      Log.i(TAG, "onConnecting()");
    }

    @Override
    public void onDisconnected() {
      Log.w(TAG, "onDisconnected()");
    }

    @Override
    public void onAuthenticationFailure() {
      Log.w(TAG, "onAuthenticationFailure()");
      TextSecurePreferences.setUnauthorizedReceived(context, true);
      EventBus.getDefault().post(new ReminderUpdateEvent());
    }

  }

}
