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

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatDelegate;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.multidex.MultiDexApplication;

import org.conscrypt.Conscrypt;

import org.signal.aesgcmprovider.AesGcmProvider;
import org.signal.ringrtc.CallManager;

import su.sres.core.util.tracing.Tracer;
import su.sres.glide.SignalGlideCodecs;
import su.sres.securesms.avatar.AvatarPickerStorage;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.LogDatabase;
import su.sres.securesms.database.SqlCipherLibraryLoader;
import su.sres.securesms.database.helpers.SQLCipherOpenHelper;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.dependencies.ApplicationDependencyProvider;
import su.sres.securesms.dependencies.NetworkIndependentProvider;
import su.sres.securesms.emoji.EmojiSource;
import su.sres.securesms.gcm.FcmJobService;
import su.sres.securesms.jobs.CertificateRefreshJob;
import su.sres.securesms.jobs.DownloadLatestEmojiDataJob;
import su.sres.securesms.jobs.EmojiSearchIndexDownloadJob;
import su.sres.securesms.jobs.GroupV1MigrationJob;
import su.sres.securesms.jobs.LicenseManagementJob;
import su.sres.securesms.jobs.MultiDeviceContactUpdateJob;
import su.sres.securesms.jobs.CreateSignedPreKeyJob;
import su.sres.securesms.jobs.FcmRefreshJob;
import su.sres.securesms.jobs.PushNotificationReceiveJob;
import su.sres.securesms.jobs.RefreshPreKeysJob;
import su.sres.securesms.jobs.RetrieveProfileJob;
import su.sres.securesms.jobs.ServiceConfigRefreshJob;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.core.util.logging.AndroidLogger;
import su.sres.securesms.logging.CustomSignalProtocolLogger;
import su.sres.core.util.logging.Log;
import su.sres.securesms.logging.PersistentLogger;
import su.sres.securesms.messageprocessingalarm.MessageProcessReceiver;
import su.sres.securesms.ratelimit.RateLimitUtil;
import su.sres.securesms.util.AppForegroundObserver;
import su.sres.securesms.util.AppStartup;
import su.sres.securesms.util.ShadowLocalMetrics;
import su.sres.securesms.util.SignalUncaughtExceptionHandler;
import su.sres.securesms.migrations.ApplicationMigrations;
import su.sres.securesms.notifications.NotificationChannels;
import su.sres.securesms.providers.BlobProvider;
import su.sres.securesms.registration.RegistrationUtil;
import su.sres.securesms.ringrtc.RingRtcLogger;
import su.sres.securesms.service.DirectoryRefreshListener;
import su.sres.securesms.service.KeyCachingService;
import su.sres.securesms.service.LocalBackupListener;
import su.sres.securesms.service.RotateSenderCertificateListener;
import su.sres.securesms.service.RotateSignedPreKeyListener;
import su.sres.securesms.service.UpdateApkRefreshListener;
import su.sres.securesms.util.DynamicTheme;
import su.sres.securesms.util.FeatureFlags;
import su.sres.securesms.util.TextSecurePreferences;
import su.sres.securesms.util.Util;
import su.sres.core.util.concurrent.SignalExecutors;
import su.sres.securesms.util.VersionTracker;
import su.sres.securesms.util.dynamiclanguage.DynamicLanguageContextWrapper;

import org.webrtc.voiceengine.WebRtcAudioManager;
import org.webrtc.voiceengine.WebRtcAudioUtils;
import org.whispersystems.libsignal.logging.SignalProtocolLoggerProvider;

import java.security.Security;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Will be called once when the TextSecure process is created.
 * <p>
 * We're using this as an insertion point to patch up the Android PRNG disaster,
 * to initialize the job manager, and to check for GCM registration freshness.
 *
 * @author Moxie Marlinspike *
 */

public class ApplicationContext extends MultiDexApplication implements AppForegroundObserver.Listener {

  private static final String TAG = Log.tag(ApplicationContext.class);

  private PersistentLogger persistentLogger;

  private boolean
      initializedOnCreate = false,
      initializedOnStart  = false;

  final InitWorker initWorker = new InitWorker();

  public static ApplicationContext getInstance(Context context) {
    return (ApplicationContext) context.getApplicationContext();
  }

  @Override
  public void onCreate() {
    Tracer.getInstance().start("Application#onCreate()");
    AppStartup.getInstance().onApplicationCreate();
    ShadowLocalMetrics.ColdStart.start();

    long startTime = System.currentTimeMillis();

    if (FeatureFlags.internalUser()) {
      Tracer.getInstance().setMaxBufferSize(35_000);
    }

    super.onCreate();

    AppStartup.getInstance().addForemost("security-provider", this::initializeSecurityProvider)
              .addForemost("sqlcipher-init", () -> SqlCipherLibraryLoader.load(this))
              .addForemost("logging", () -> {
                initializeLogging();
                Log.i(TAG, "onCreate()");
              })
              .addForemost("crash-handling", this::initializeCrashHandling)
              .addForemost("rx-init", () -> {
                RxJavaPlugins.setInitIoSchedulerHandler(schedulerSupplier -> Schedulers.from(SignalExecutors.BOUNDED_IO, true, false));
                RxJavaPlugins.setInitComputationSchedulerHandler(schedulerSupplier -> Schedulers.from(SignalExecutors.BOUNDED, true, false));
              })
              .addForemost("app-network-independent-dependencies", this::initializeNetworkIndependentProvider)
              .addForemost("app-network-dependent-dependencies", this::initializeNetworkDependentProvider)
              .addForemost("notification-channels", () -> NotificationChannels.create(this))
              .addForemost("proxy-init", () -> {
                if (SignalStore.proxy().isProxyEnabled()) {
                  Log.w(TAG, "Proxy detected. Enabling Conscrypt.setUseEngineSocketByDefault()");
                  Conscrypt.setUseEngineSocketByDefault(true);
                }
              })
              .executeForemost();

    // checking at subsequent launches of the app, if the server is already known as set in SignalStore, then no need for delay, just initialize immediately
    if (SignalStore.registrationValues().isServerSet()) {
      initializeOnCreate();
    } else {

      SignalExecutors.BOUNDED.execute(() -> {

        while (!initializedOnCreate) {

          if (SignalStore.registrationValues().isServerSet()) {
            initializeOnCreate();
          } else {
            Log.i(TAG, "Waiting for the server URL to be configured...");
            try {
              Thread.sleep(3000);
            } catch (InterruptedException e) {
              e.printStackTrace();
            }
          }
        }
      });
    }

    Log.d(TAG, "onCreate() took " + (System.currentTimeMillis() - startTime) + " ms");
    ShadowLocalMetrics.ColdStart.onApplicationCreateFinished();
    Tracer.getInstance().end("Application#onCreate()");
  }

  @Override
  public void onForeground() {
    long startTime = System.currentTimeMillis();

    Log.i(TAG, "App is now visible.");

    initWorker.execute(() -> {

      while (!initializedOnStart) {

        if (SignalStore.registrationValues().isServerSet() && initializedOnCreate) {
          FeatureFlags.refreshIfNecessary();
          ApplicationDependencies.getRecipientCache().warmUp();
          RetrieveProfileJob.enqueueRoutineFetchIfNecessary(this);
          GroupV1MigrationJob.enqueueRoutineMigrationsIfNecessary(this);
          executePendingContactSync();
          KeyCachingService.onAppForegrounded(this);
          ApplicationDependencies.getShakeToReport().enable();
          ApplicationDependencies.getFrameRateTracker().begin();
          ApplicationDependencies.getMegaphoneRepository().onAppForegrounded();
          launchCertificateRefresh();
          launchLicenseRefresh();
          launchServiceConfigRefresh();
          checkBuildExpiration();

          initializedOnStart = true;

        } else {
          Log.i(TAG, "Waiting for initialization to complete...");
          try {
            Thread.sleep(3000);
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
        }
      }
    });

    Log.d(TAG, "onStart() took " + (System.currentTimeMillis() - startTime) + " ms");
  }

  @Override
  public void onBackground() {
    Log.i(TAG, "App is no longer visible.");
    KeyCachingService.onAppBackgrounded(this);

    // the if clause is to prevent crash when going out of focus in unprovisioned state
    if (initializedOnCreate) {
      ApplicationDependencies.getMessageNotifier().clearVisibleThread();
      ApplicationDependencies.getShakeToReport().disable();
      initWorker.execute(() -> {
        ApplicationDependencies.getFrameRateTracker().end();
      });
    }


    initializedOnStart = false;
  }

  public PersistentLogger getPersistentLogger() {
    return persistentLogger;
  }

  public void checkBuildExpiration() {
    if (Util.getTimeUntilBuildExpiry() <= 0 && !SignalStore.misc().isClientDeprecated()) {
      Log.w(TAG, "Build expired!");
      SignalStore.misc().markClientDeprecated();
    }
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
    su.sres.core.util.logging.Log.initialize(FeatureFlags::internalUser, new AndroidLogger(), persistentLogger);

    SignalProtocolLoggerProvider.setProvider(new CustomSignalProtocolLogger());

    SignalExecutors.UNBOUNDED.execute(() -> LogDatabase.getInstance(this).trimToSize());
  }

  private void initializeCrashHandling() {
    final Thread.UncaughtExceptionHandler originalHandler = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler(new SignalUncaughtExceptionHandler(originalHandler));
  }

  private void initializeApplicationMigrations() {
    ApplicationMigrations.onApplicationCreate(this, ApplicationDependencies.getJobManager());
  }

  public void initializeMessageRetrieval() {
    ApplicationDependencies.getIncomingMessageObserver();
  }

  private void initializeNetworkDependentProvider() {
    ApplicationDependencies.networkDependentProviderInit(new ApplicationDependencyProvider(this));
  }

  private void initializeNetworkIndependentProvider() {
    ApplicationDependencies.networkIndependentProviderInit(this, new NetworkIndependentProvider(this));
  }

  private void initializeFirstEverAppLaunch() {
    if (TextSecurePreferences.getFirstInstallVersion(this) == -1) {
      if (!SQLCipherOpenHelper.databaseFileExists(this) || VersionTracker.getDaysSinceFirstInstalled(this) < 365) {
        Log.i(TAG, "First ever app launch!");

        AppInitialization.onFirstEverAppLaunch(this);
      }

      Log.i(TAG, "Setting first install version to " + BuildConfig.CANONICAL_VERSION_CODE);
      TextSecurePreferences.setFirstInstallVersion(this, BuildConfig.CANONICAL_VERSION_CODE);
    } else if (!TextSecurePreferences.isPasswordDisabled(this) && VersionTracker.getDaysSinceFirstInstalled(this) < 90) {
      Log.i(TAG, "Detected a new install that doesn't have passphrases disabled -- assuming bad initialization.");
      AppInitialization.onRepairFirstEverAppLaunch(this);
    } else if (!TextSecurePreferences.isPasswordDisabled(this) && VersionTracker.getDaysSinceFirstInstalled(this) < 912) {
      Log.i(TAG, "Detected a not-recent install that doesn't have passphrases disabled -- disabling now.");
      TextSecurePreferences.setPasswordDisabled(this, true);
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
    ApplicationDependencies.getExpiringMessageManager().checkSchedule();
  }

  private void initializeRevealableMessageManager() {
    ApplicationDependencies.getViewOnceMessageManager().scheduleIfNecessary();
  }

  private void initializePendingRetryReceiptManager() {
    ApplicationDependencies.getPendingRetryReceiptManager().scheduleIfNecessary();
  }

  private void initializePeriodicTasks() {
    RotateSignedPreKeyListener.schedule(this);
    DirectoryRefreshListener.schedule(this);
    LocalBackupListener.schedule(this);
    RotateSenderCertificateListener.schedule(this);
    MessageProcessReceiver.startOrUpdateAlarm(this);

    if (BuildConfig.PLAY_STORE_DISABLED) {
      UpdateApkRefreshListener.schedule(this);
    }
  }

  private void initializeRingRtc() {
    try {
      if (RtcDeviceLists.hardwareAECBlocked()) {
        WebRtcAudioUtils.setWebRtcBasedAcousticEchoCanceler(true);
      }

      if (!RtcDeviceLists.openSLESAllowed()) {
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
        ApplicationDependencies.getJobManager().add(new PushNotificationReceiveJob());
      }
      TextSecurePreferences.setNeedsMessagePull(this, false);
    }
  }

  @WorkerThread
  private void initializeBlobProvider() {
    BlobProvider.getInstance().initialize(this);
  }

  @WorkerThread
  private void cleanAvatarStorage() {
    AvatarPickerStorage.cleanOrphans(this);
  }

  @WorkerThread
  private void initializeCleanup() {
    int deleted = DatabaseFactory.getAttachmentDatabase(this).deleteAbandonedPreuploadedAttachments();
    Log.i(TAG, "Deleted " + deleted + " abandoned attachments.");
  }

  private void initializeMasterKey() {
    // generate a random "master key"
//    byte[] masterKey = new byte[32];
//    SecureRandom random = new SecureRandom();
//    random.nextBytes(masterKey);

    // set just a filler for now
    SignalStore.kbsValues().setKbsMasterKey(SignalStore.kbsValues().getOrCreateMasterKey());
  }

  private void initializeGlideCodecs() {
    SignalGlideCodecs.setLogProvider(new su.sres.glide.Log.Provider() {
      @Override
      public void v(@NonNull String tag, @NonNull String message) {
        Log.v(tag, message);
      }

      @Override
      public void d(@NonNull String tag, @NonNull String message) {
        Log.d(tag, message);
      }

      @Override
      public void i(@NonNull String tag, @NonNull String message) {
        Log.i(tag, message);
      }

      @Override
      public void w(@NonNull String tag, @NonNull String message) {
        Log.w(tag, message);
      }

      @Override
      public void e(@NonNull String tag, @NonNull String message, @Nullable Throwable throwable) {
        Log.e(tag, message, throwable);
      }
    });
  }

  @Override
  protected void attachBaseContext(Context base) {
    DynamicLanguageContextWrapper.updateContext(base);
    super.attachBaseContext(base);
  }

  private static class ProviderInitializationException extends RuntimeException {
  }

  private void initializeOnCreate() {

    AppStartup.getInstance()
              .addBlocking("first-launch", this::initializeFirstEverAppLaunch)
              .addBlocking("app-migrations", this::initializeApplicationMigrations)
              .addBlocking("ring-rtc", this::initializeRingRtc)
              .addBlocking("mark-registration", () -> RegistrationUtil.maybeMarkRegistrationComplete(this))
              .addBlocking("lifecycle-observer", () -> ApplicationDependencies.getAppForegroundObserver().addListener(this))
              .addBlocking("message-retriever", this::initializeMessageRetrieval)
              .addBlocking("dynamic-theme", () -> DynamicTheme.setDefaultDayNightMode(this))
              .addBlocking("vector-compat", () -> {AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);})
              .addBlocking("blob-provider", this::initializeBlobProvider)
              .addBlocking("feature-flags", FeatureFlags::init)
              .addNonBlocking(this::cleanAvatarStorage)
              .addNonBlocking(this::initializeRevealableMessageManager)
              .addNonBlocking(this::initializePendingRetryReceiptManager)
              .addNonBlocking(this::initializeGcmCheck)
              .addNonBlocking(this::initializeSignedPreKeyCheck)
              .addNonBlocking(this::initializePeriodicTasks)
              .addNonBlocking(this::initializePendingMessages)
              .addNonBlocking(this::initializeCleanup)
              .addNonBlocking(this::initializeGlideCodecs)
              .addNonBlocking(this::initializeMasterKey)
              .addNonBlocking(RefreshPreKeysJob::scheduleIfNecessary)
              // .addNonBlocking(StorageSyncHelper::scheduleRoutineSync)
              .addNonBlocking(() -> ApplicationDependencies.getJobManager().beginJobLoop())
              .addNonBlocking(EmojiSource::refresh)
              .addPostRender(() -> RateLimitUtil.retryAllRateLimitedMessages(this))
              .addPostRender(this::initializeExpiringMessageManager)
              .addPostRender(() -> DownloadLatestEmojiDataJob.scheduleIfNecessary(this))
              .addPostRender(EmojiSearchIndexDownloadJob::scheduleIfNecessary)
              .addPostRender(() -> DatabaseFactory.getMessageLogDatabase(this).trimOldMessages(System.currentTimeMillis(), FeatureFlags.retryRespondMaxAge()))
              .execute();

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
