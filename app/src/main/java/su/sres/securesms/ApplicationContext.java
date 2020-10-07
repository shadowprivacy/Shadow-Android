/*
 * Copyright (C) 2013 Open Whisper Systems, modifications (C) 2020 Anton Alipov, sole trader
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package su.sres.securesms;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.multidex.MultiDexApplication;

import org.conscrypt.Conscrypt;

import org.signal.aesgcmprovider.AesGcmProvider;
import su.sres.ringrtc.CallManager;
import su.sres.securesms.components.TypingStatusRepository;
import su.sres.securesms.components.TypingStatusSender;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.helpers.SQLCipherOpenHelper;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.dependencies.ApplicationDependencyProvider;
import su.sres.securesms.dependencies.NetworkIndependentProvider;
import su.sres.securesms.gcm.FcmJobService;
import su.sres.securesms.jobs.CertificateRefreshJob;
import su.sres.securesms.jobs.LicenseManagementJob;
import su.sres.securesms.jobs.MultiDeviceContactUpdateJob;
import su.sres.securesms.jobs.CreateSignedPreKeyJob;
import su.sres.securesms.jobs.FcmRefreshJob;
import su.sres.securesms.jobs.PushNotificationReceiveJob;
import su.sres.securesms.jobs.RefreshPreKeysJob;
import su.sres.securesms.jobs.RetrieveProfileJob;
import su.sres.securesms.jobs.ServiceConfigRefreshJob;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.securesms.logging.AndroidLogger;
import su.sres.securesms.logging.CustomSignalProtocolLogger;
import su.sres.securesms.logging.Log;
import su.sres.securesms.logging.PersistentLogger;
import su.sres.securesms.logging.SignalUncaughtExceptionHandler;
import su.sres.securesms.messages.InitialMessageRetriever;
import su.sres.securesms.migrations.ApplicationMigrations;
import su.sres.securesms.notifications.NotificationChannels;
import su.sres.securesms.providers.BlobProvider;
import su.sres.securesms.push.SignalServiceNetworkAccess;
import su.sres.securesms.registration.RegistrationUtil;
import su.sres.securesms.revealable.ViewOnceMessageManager;
import su.sres.securesms.ringrtc.RingRtcLogger;
import su.sres.securesms.service.DirectoryRefreshListener;
import su.sres.securesms.service.ExpiringMessageManager;
import su.sres.securesms.messages.IncomingMessageObserver;
import su.sres.securesms.service.KeyCachingService;
import su.sres.securesms.service.LocalBackupListener;
import su.sres.securesms.service.RotateSenderCertificateListener;
import su.sres.securesms.service.RotateSignedPreKeyListener;
import su.sres.securesms.service.UpdateApkRefreshListener;
import su.sres.securesms.storage.StorageSyncHelper;
import su.sres.securesms.util.FeatureFlags;
import su.sres.securesms.util.PlayServicesUtil;
import su.sres.securesms.util.TextSecurePreferences;
import su.sres.securesms.util.concurrent.SignalExecutors;
import su.sres.securesms.util.dynamiclanguage.DynamicLanguageContextWrapper;

import org.webrtc.voiceengine.WebRtcAudioManager;
import org.webrtc.voiceengine.WebRtcAudioUtils;
import org.whispersystems.libsignal.logging.SignalProtocolLoggerProvider;

import java.security.Security;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Will be called once when the TextSecure process is created.
 *
 * We're using this as an insertion point to patch up the Android PRNG disaster,
 * to initialize the job manager, and to check for GCM registration freshness.
 *
 * @author Moxie Marlinspike
 */

public class ApplicationContext extends MultiDexApplication implements DefaultLifecycleObserver {

  private static final String TAG = ApplicationContext.class.getSimpleName();

  private ExpiringMessageManager   expiringMessageManager;
  private ViewOnceMessageManager   viewOnceMessageManager;
  private TypingStatusRepository   typingStatusRepository;
  private TypingStatusSender       typingStatusSender;
  private IncomingMessageObserver  incomingMessageObserver;
  private PersistentLogger         persistentLogger;

  private volatile boolean isAppVisible;

  private boolean
                  initializedOnCreate = false,
                  initializedOnStart  = false;

  final InitWorker initWorker = new InitWorker();

  public static ApplicationContext getInstance(Context context) {
    return (ApplicationContext)context.getApplicationContext();
  }

  @Override
  public void onCreate() {
    super.onCreate();

    Log.i(TAG, "onCreate()");

    initializeSecurityProvider();
    initializeLogging();
    initializeCrashHandling();
    initializeNetworkIndependentProvider();

      // checking at subsequent launches of the app, if the server is already known as set in SignalStore, then no need for delay, just initialize immediately
      if (SignalStore.registrationValues().isServerSet()) {
          initializeOnCreate();
      } else {

          initWorker.execute(() -> {

              while (!initializedOnCreate) {

                  if (SignalStore.registrationValues().isServerSet()) {
                      initializeOnCreate();
                  } else {
                      Log.i(TAG, "Waiting for the server URL to be configured...");
                      try {
                          Thread.sleep(2000);
                      } catch (InterruptedException e) {
                          e.printStackTrace();
                      }
                  }
              }
          });
      }

    NotificationChannels.create(this);
    ProcessLifecycleOwner.get().getLifecycle().addObserver(this);

    if (Build.VERSION.SDK_INT < 21) {
      AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
    }
  }

  @Override
  public void onStart(@NonNull LifecycleOwner owner) {

    isAppVisible = true;
    Log.i(TAG, "App is now visible.");

    initWorker.execute(() -> {

      while(!initializedOnStart) {

        if (SignalStore.registrationValues().isServerSet() && initializedOnCreate) {
          FeatureFlags.refreshIfNecessary();
          ApplicationDependencies.getRecipientCache().warmUp();
          ApplicationDependencies.getFrameRateTracker().begin();
          ApplicationDependencies.getMegaphoneRepository().onAppForegrounded();

          // the if clause is to prevent creation of message receiver until we have the cloud URL in store
          if (TextSecurePreferences.isPushRegistered(this)) {
            catchUpOnMessages();
          }

          initializedOnStart = true;

        } else {
          Log.i(TAG, "Waiting for initialization to complete...");
          try {
            Thread.sleep(2000);
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
        }
      }
    });

    executePendingContactSync();
    KeyCachingService.onAppForegrounded(this);
  }

  @Override
  public void onStop(@NonNull LifecycleOwner owner) {
    isAppVisible = false;
    Log.i(TAG, "App is no longer visible.");
    KeyCachingService.onAppBackgrounded(this);

    // the if clause is to prevent crash when going out of focus in unprovisioned state
    if (initializedOnCreate) {
      ApplicationDependencies.getMessageNotifier().clearVisibleThread();
    }

    initWorker.execute(() -> {
                ApplicationDependencies.getFrameRateTracker().end();
            });

    initializedOnStart = false;
  }

  public ExpiringMessageManager getExpiringMessageManager() {
    return expiringMessageManager;
  }

  public ViewOnceMessageManager getViewOnceMessageManager() {
    return viewOnceMessageManager;
  }

  public TypingStatusRepository getTypingStatusRepository() {
    return typingStatusRepository;
  }

  public TypingStatusSender getTypingStatusSender() {
    return typingStatusSender;
  }

  public boolean isAppVisible() {
    return isAppVisible;
  }

  public PersistentLogger getPersistentLogger() {
    return persistentLogger;
  }

  private void initializeSecurityProvider() {
    try {
      Class.forName("org.signal.aesgcmprovider.AesGcmCipher");
    } catch (ClassNotFoundException e) {
      Log.e(TAG, "Failed to find AesGcmCipher class");
      throw new ProviderInitializationException();
    }

    int aesPosition = Security.insertProviderAt(new AesGcmProvider(), 1);
    Log.i(TAG, "Installed AesGcmProvider: " + aesPosition);

    if (aesPosition < 0) {
      Log.e(TAG, "Failed to install AesGcmProvider()");
      throw new ProviderInitializationException();
    }

    int conscryptPosition = Security.insertProviderAt(Conscrypt.newProvider(), 2);
    Log.i(TAG, "Installed Conscrypt provider: " + conscryptPosition);

    if (conscryptPosition < 0) {
      Log.w(TAG, "Did not install Conscrypt provider. May already be present.");
    }
  }

  private void initializeLogging() {
    persistentLogger = new PersistentLogger(this);
    su.sres.securesms.logging.Log.initialize(new AndroidLogger(), persistentLogger);

    SignalProtocolLoggerProvider.setProvider(new CustomSignalProtocolLogger());
  }

  private void initializeCrashHandling() {
    final Thread.UncaughtExceptionHandler originalHandler = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler(new SignalUncaughtExceptionHandler(originalHandler));
  }

  private void initializeApplicationMigrations() {
    ApplicationMigrations.onApplicationCreate(this, ApplicationDependencies.getJobManager());
  }

  public void initializeMessageRetrieval() {

    this.incomingMessageObserver = new IncomingMessageObserver(this);
  }

  private void initializeNetworkDependentProvider() {
    ApplicationDependencies.networkDependentProviderInit(new ApplicationDependencyProvider(this, new SignalServiceNetworkAccess(this)));
  }

  private void initializeNetworkIndependentProvider() {
      ApplicationDependencies.networkIndependentProviderInit(this, new NetworkIndependentProvider(this));
  }

  private void initializeFirstEverAppLaunch() {
    if (TextSecurePreferences.getFirstInstallVersion(this) == -1) {
      if (!SQLCipherOpenHelper.databaseFileExists(this)) {
        Log.i(TAG, "First ever app launch!");

        AppInitialization.onFirstEverAppLaunch(this);
      }

      Log.i(TAG, "Setting first install version to " + BuildConfig.CANONICAL_VERSION_CODE);
      TextSecurePreferences.setFirstInstallVersion(this, BuildConfig.CANONICAL_VERSION_CODE);
    }
  }

  private void initializeGcmCheck() {
    if (TextSecurePreferences.isPushRegistered(this)) {
      long nextSetTime = TextSecurePreferences.getFcmTokenLastSetTime(this) + TimeUnit.HOURS.toMillis(6);

      if (TextSecurePreferences.getFcmToken(this) == null || nextSetTime <= System.currentTimeMillis()) {
        ApplicationDependencies.getJobManager().add(new FcmRefreshJob());
      }
    }
  }

  private void initializeSignedPreKeyCheck() {
    if (!TextSecurePreferences.isSignedPreKeyRegistered(this)) {
      ApplicationDependencies.getJobManager().add(new CreateSignedPreKeyJob(this));
    }
  }

  private void initializeExpiringMessageManager() {
    this.expiringMessageManager = new ExpiringMessageManager(this);
  }

  private void initializeRevealableMessageManager() {
    this.viewOnceMessageManager = new ViewOnceMessageManager(this);
  }

  private void initializeTypingStatusRepository() {
    this.typingStatusRepository = new TypingStatusRepository();
  }

  private void initializeTypingStatusSender() {
    this.typingStatusSender = new TypingStatusSender(this);
  }

  private void initializePeriodicTasks() {
    RotateSignedPreKeyListener.schedule(this);
    DirectoryRefreshListener.schedule(this);
    LocalBackupListener.schedule(this);
    RotateSenderCertificateListener.schedule(this);

    if (BuildConfig.PLAY_STORE_DISABLED) {
      UpdateApkRefreshListener.schedule(this);
    }
  }

  private void initializeRingRtc() {
    try {
      Set<String> HARDWARE_AEC_BLACKLIST = new HashSet<String>() {{
        add("Pixel");
        add("Pixel XL");
        add("Moto G5");
        add("Moto G (5S) Plus");
        add("Moto G4");
        add("TA-1053");
        add("Mi A1");
        add("Mi A2");
        add("E5823"); // Sony z5 compact
        add("Redmi Note 5");
        add("FP2"); // Fairphone FP2
        add("MI 5");
      }};

      Set<String> OPEN_SL_ES_WHITELIST = new HashSet<String>() {{
        add("Pixel");
        add("Pixel XL");
      }};

      if (HARDWARE_AEC_BLACKLIST.contains(Build.MODEL)) {
        WebRtcAudioUtils.setWebRtcBasedAcousticEchoCanceler(true);
      }

      if (!OPEN_SL_ES_WHITELIST.contains(Build.MODEL)) {
        WebRtcAudioManager.setBlacklistDeviceForOpenSLESUsage(true);
      }

        CallManager.initialize(this, new RingRtcLogger());
    } catch (UnsatisfiedLinkError e) {
        throw new AssertionError("Unable to load ringrtc library", e);
    }
  }

  private void executePendingContactSync() {
    if (TextSecurePreferences.needsFullContactSync(this)) {
      ApplicationDependencies.getJobManager().add(new MultiDeviceContactUpdateJob(true));
    }
  }

  private void initializePendingMessages() {
    if (TextSecurePreferences.getNeedsMessagePull(this)) {
      Log.i(TAG, "Scheduling a message fetch.");
      if (Build.VERSION.SDK_INT >= 26) {
        FcmJobService.schedule(this);
      } else {
        ApplicationDependencies.getJobManager().add(new PushNotificationReceiveJob(this));
      }
      TextSecurePreferences.setNeedsMessagePull(this, false);
    }
  }

  private void initializeBlobProvider() {
      SignalExecutors.BOUNDED.execute(() -> {
      BlobProvider.getInstance().onSessionStart(this);
    });
  }

    private void initializeCleanup() {
        SignalExecutors.BOUNDED.execute(() -> {
            int deleted = DatabaseFactory.getAttachmentDatabase(this).deleteAbandonedPreuploadedAttachments();
            Log.i(TAG, "Deleted " + deleted + " abandoned attachments.");
        });
    }

  private void initializeMasterKey() {
    // generate a random "master key"
//    byte[] masterKey = new byte[32];
//    SecureRandom random = new SecureRandom();
//    random.nextBytes(masterKey);

    // set just a filler for now
    SignalStore.kbsValues().setKbsMasterKey(SignalStore.kbsValues().getOrCreateMasterKey());
  }

  private void catchUpOnMessages() {
    InitialMessageRetriever retriever = ApplicationDependencies.getInitialMessageRetriever();

    if (retriever.isCaughtUp()) {
      return;
    }

    SignalExecutors.UNBOUNDED.execute(() -> {
      long startTime = System.currentTimeMillis();

      switch (retriever.begin(TimeUnit.SECONDS.toMillis(60))) {
        case SUCCESS:
          Log.i(TAG, "Successfully caught up on messages. " + (System.currentTimeMillis() - startTime) + " ms");
          break;
        case FAILURE_TIMEOUT:
          Log.w(TAG, "Did not finish catching up due to a timeout. " + (System.currentTimeMillis() - startTime) + " ms");
          break;
        case FAILURE_ERROR:
          Log.w(TAG, "Did not finish catching up due to an error. " + (System.currentTimeMillis() - startTime) + " ms");
          break;
        case SKIPPED_ALREADY_CAUGHT_UP:
          Log.i(TAG, "Already caught up. " + (System.currentTimeMillis() - startTime) + " ms");
          break;
        case SKIPPED_ALREADY_RUNNING:
          Log.i(TAG, "Already in the process of catching up. " + (System.currentTimeMillis() - startTime) + " ms");
          break;
      }
    });
  }

  @Override
  protected void attachBaseContext(Context base) {
    super.attachBaseContext(DynamicLanguageContextWrapper.updateContext(base, TextSecurePreferences.getLanguage(base)));
  }

  private static class ProviderInitializationException extends RuntimeException {
  }

  private void initializeOnCreate() {

    initializeNetworkDependentProvider();
    initializeFirstEverAppLaunch();
    initializeApplicationMigrations();
    initializeMessageRetrieval();
    initializeExpiringMessageManager();
    initializeRevealableMessageManager();
    initializeTypingStatusRepository();
    initializeTypingStatusSender();
    initializeGcmCheck();
    initializeSignedPreKeyCheck();
    initializePeriodicTasks();
    initializeRingRtc();
    initializePendingMessages();
    initializeBlobProvider();
    initializeCleanup();
    FeatureFlags.init();
    initializeMasterKey();
    ApplicationDependencies.getJobManager().beginJobLoop();
//    StorageSyncHelper.scheduleRoutineSync();
    RetrieveProfileJob.enqueueRoutineFetchIfNeccessary(this);
    RegistrationUtil.markRegistrationPossiblyComplete();
    RefreshPreKeysJob.scheduleIfNecessary();
    launchCertificateRefresh();
    launchLicenseRefresh();
    launchServiceConfigRefresh();

    initializedOnCreate = true;
  }

  private void launchCertificateRefresh() {
      if (TextSecurePreferences.isPushRegistered(this)) {
          CertificateRefreshJob.scheduleIfNecessary();
      } else {
          Log.i(TAG, "The client is not registered. Certificate refresh will not be triggered.");
      }
  }

  private void launchLicenseRefresh() {
      if (TextSecurePreferences.isPushRegistered(this)) {
          LicenseManagementJob.scheduleIfNecessary();
      } else {
          Log.i(TAG, "The client is not registered. License refresh will not be triggered.");
      }
  }

  private void launchServiceConfigRefresh() {
    if (TextSecurePreferences.isPushRegistered(this)) {
      ServiceConfigRefreshJob.scheduleIfNecessary();
    } else {
      Log.i(TAG, "The client is not registered. Service configuration refresh will not be triggered.");
    }
  }
}
