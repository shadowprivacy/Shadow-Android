/*
 * Copyright (C) 2013 Open Whisper Systems, modifications (C) 2019 Sophisticated Research
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

import android.annotation.SuppressLint;

import androidx.camera.camera2.Camera2AppConfig;
import androidx.camera.core.CameraX;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.multidex.MultiDexApplication;

import com.google.android.gms.security.ProviderInstaller;

import org.conscrypt.Conscrypt;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import org.signal.aesgcmprovider.AesGcmProvider;
import su.sres.securesms.components.TypingStatusRepository;
import su.sres.securesms.components.TypingStatusSender;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.dependencies.AxolotlStorageModule;
import su.sres.securesms.dependencies.InjectableType;
import su.sres.securesms.dependencies.SignalCommunicationModule;
import su.sres.securesms.gcm.FcmJobService;
import su.sres.securesms.events.ServerSetEvent;
import su.sres.securesms.jobmanager.DependencyInjector;
import su.sres.securesms.jobmanager.JobManager;
import su.sres.securesms.jobs.FastJobStorage;
import su.sres.securesms.jobs.JobManagerFactories;
import su.sres.securesms.jobmanager.impl.JsonDataSerializer;
import su.sres.securesms.jobs.CreateSignedPreKeyJob;
import su.sres.securesms.jobs.FcmRefreshJob;
import su.sres.securesms.jobs.PushNotificationReceiveJob;
import su.sres.securesms.jobs.RefreshUnidentifiedDeliveryAbilityJob;
import su.sres.securesms.logging.AndroidLogger;
import su.sres.securesms.logging.CustomSignalProtocolLogger;
import su.sres.securesms.logging.Log;
import su.sres.securesms.logging.PersistentLogger;
import su.sres.securesms.logging.UncaughtExceptionLogger;
import su.sres.securesms.notifications.MessageNotifier;
import su.sres.securesms.notifications.NotificationChannels;
import su.sres.securesms.providers.BlobProvider;
import su.sres.securesms.push.SignalServiceNetworkAccess;
import su.sres.securesms.service.DirectoryRefreshListener;
import su.sres.securesms.service.ExpiringMessageManager;
import su.sres.securesms.service.IncomingMessageObserver;
import su.sres.securesms.service.KeyCachingService;
import su.sres.securesms.service.LocalBackupListener;
import su.sres.securesms.service.RotateSenderCertificateListener;
import su.sres.securesms.service.RotateSignedPreKeyListener;
import su.sres.securesms.service.UpdateApkRefreshListener;
import su.sres.securesms.util.TextSecurePreferences;
import su.sres.securesms.util.dynamiclanguage.DynamicLanguageContextWrapper;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.PeerConnectionFactory.InitializationOptions;
import org.webrtc.voiceengine.WebRtcAudioManager;
import org.webrtc.voiceengine.WebRtcAudioUtils;
import org.whispersystems.libsignal.logging.SignalProtocolLoggerProvider;

import java.security.Security;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import dagger.ObjectGraph;

/**
 * Will be called once when the TextSecure process is created.
 *
 * We're using this as an insertion point to patch up the Android PRNG disaster,
 * to initialize the job manager, and to check for GCM registration freshness.
 *
 * @author Moxie Marlinspike
 */
public class ApplicationContext extends MultiDexApplication implements DependencyInjector, DefaultLifecycleObserver {

  private static final String TAG = ApplicationContext.class.getSimpleName();

  private ExpiringMessageManager  expiringMessageManager;
  private TypingStatusRepository  typingStatusRepository;
  private TypingStatusSender      typingStatusSender;
  private JobManager jobManager;
  private IncomingMessageObserver incomingMessageObserver;
  private ObjectGraph             objectGraph;
  private PersistentLogger        persistentLogger;

  private volatile boolean isAppVisible;

  private boolean isserverset;
  private final String DEFAULT_SERVER_URL = "https://example.org";

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

    // register to Event Bus
    EventBus.getDefault().register(this);

    // checking at subsequent launches of the app, if the server is already known in the DB, then no need for delay, just initialize immediately
    if (!DatabaseFactory.getConfigDatabase(this).getConfigById(1).equals(DEFAULT_SERVER_URL)) {
      isserverset = true;
      initializeDependencyInjection();
      initializeJobManager();
      initializeMessageRetrieval();
      initializeExpiringMessageManager();
      initializeTypingStatusRepository();
      initializeTypingStatusSender();
      initializeGcmCheck();
      initializeSignedPreKeyCheck();
      initializePeriodicTasks();
      initializeCircumvention();
      initializeWebRtc();
      initializePendingMessages();
      initializeUnidentifiedDeliveryAbilityRefresh();
      initializeBlobProvider();
      initializeCameraX();
    }

    NotificationChannels.create(this);
    ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
  }

  @Override
  public void onStart(@NonNull LifecycleOwner owner) {

   // check if we're already registered, if not then register; this is needed for the case when the app is brought back on top
    if (!EventBus.getDefault().isRegistered(this)) {
      EventBus.getDefault().register(this);
    }

    isAppVisible = true;
    Log.i(TAG, "App is now visible.");
    executePendingContactSync();
    KeyCachingService.onAppForegrounded(this);
    MessageNotifier.cancelMessagesPending(this);
  }

  @Override
  public void onStop(@NonNull LifecycleOwner owner) {
    isAppVisible = false;
    Log.i(TAG, "App is no longer visible.");
    KeyCachingService.onAppBackgrounded(this);
    MessageNotifier.setVisibleThread(-1);

    // unregister from Event Bus
    EventBus.getDefault().unregister(this);
  }

  @Override
  public void injectDependencies(Object object) {
    if (object instanceof InjectableType) {
      objectGraph.inject(object);
    }
  }

  public JobManager getJobManager() {
    return jobManager;
  }

  public ExpiringMessageManager getExpiringMessageManager() {
    return expiringMessageManager;
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
    Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionLogger(originalHandler));
  }

  private void initializeJobManager() {
    this.jobManager = new JobManager(this, new JobManager.Configuration.Builder()
            .setDataSerializer(new JsonDataSerializer())
            .setJobFactories(JobManagerFactories.getJobFactories(this))
            .setConstraintFactories(JobManagerFactories.getConstraintFactories(this))
            .setConstraintObservers(JobManagerFactories.getConstraintObservers(this))
            .setJobStorage(new FastJobStorage(DatabaseFactory.getJobDatabase(this)))
            .setDependencyInjector(this)
            .build());
  }

  public void initializeMessageRetrieval() {
    this.incomingMessageObserver = new IncomingMessageObserver(this);
  }

  private void initializeDependencyInjection() {
    this.objectGraph = ObjectGraph.create(new SignalCommunicationModule(this, new SignalServiceNetworkAccess(this)),
                                          new AxolotlStorageModule(this));
  }

  private void initializeGcmCheck() {
    if (TextSecurePreferences.isPushRegistered(this)) {
      long nextSetTime = TextSecurePreferences.getFcmTokenLastSetTime(this) + TimeUnit.HOURS.toMillis(6);

      if (TextSecurePreferences.getFcmToken(this) == null || nextSetTime <= System.currentTimeMillis()) {
        this.jobManager.add(new FcmRefreshJob());
      }
    }
  }

  private void initializeSignedPreKeyCheck() {
    if (!TextSecurePreferences.isSignedPreKeyRegistered(this)) {
      jobManager.add(new CreateSignedPreKeyJob(this));
    }
  }

  private void initializeExpiringMessageManager() {
    this.expiringMessageManager = new ExpiringMessageManager(this);
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

  private void initializeWebRtc() {
    try {
      Set<String> HARDWARE_AEC_BLACKLIST = new HashSet<String>() {{
        add("Pixel");
        add("Pixel XL");
        add("Moto G5");
        add("Moto G (5S) Plus");
        add("Moto G4");
        add("TA-1053");
        add("Mi A1");
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

      PeerConnectionFactory.initialize(InitializationOptions.builder(this).createInitializationOptions());
    } catch (UnsatisfiedLinkError e) {
      Log.w(TAG, e);
    }
  }

  @SuppressLint("StaticFieldLeak")
  private void initializeCircumvention() {
    AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        if (new SignalServiceNetworkAccess(ApplicationContext.this).isCensored(ApplicationContext.this)) {
          try {
            ProviderInstaller.installIfNeeded(ApplicationContext.this);
          } catch (Throwable t) {
            Log.w(TAG, t);
          }
        }
        return null;
      }
    };

    task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  private void executePendingContactSync() {
    if (TextSecurePreferences.needsFullContactSync(this)) {
      if (Build.VERSION.SDK_INT >= 26) {
        FcmJobService.schedule(this);
      } else {
        ApplicationContext.getInstance(this).getJobManager().add(new PushNotificationReceiveJob(this));
      }
    }
  }

  private void initializePendingMessages() {
    if (TextSecurePreferences.getNeedsMessagePull(this)) {
      Log.i(TAG, "Scheduling a message fetch.");
      ApplicationContext.getInstance(this).getJobManager().add(new PushNotificationReceiveJob(this));
      TextSecurePreferences.setNeedsMessagePull(this, false);
    }
  }

  private void initializeUnidentifiedDeliveryAbilityRefresh() {
    if (TextSecurePreferences.isMultiDevice(this) && !TextSecurePreferences.isUnidentifiedDeliveryEnabled(this)) {
      jobManager.add(new RefreshUnidentifiedDeliveryAbilityJob());
    }
  }

  private void initializeBlobProvider() {
    AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> {
      BlobProvider.getInstance().onSessionStart(this);
    });
  }

  @SuppressLint("RestrictedApi")
  private void initializeCameraX() {
    if (Build.VERSION.SDK_INT >= 21) {
      try {
        CameraX.init(this, Camera2AppConfig.create(this));
      } catch (Throwable t) {
        Log.w(TAG, "Failed to initialize CameraX.");
      }
    }
  }

  @Override
  protected void attachBaseContext(Context base) {
    super.attachBaseContext(DynamicLanguageContextWrapper.updateContext(base, TextSecurePreferences.getLanguage(base)));
  }

    public boolean getserverset() {
        return isserverset;
    }

    public void setserverset(boolean flag) {
        isserverset = flag;
    }

    @Subscribe
    public void onServerSetEvent(ServerSetEvent event) {
        initializeDependencyInjection();
        initializeJobManager();
        initializeMessageRetrieval();
        initializeExpiringMessageManager();
        initializeTypingStatusRepository();
        initializeTypingStatusSender();
        initializeGcmCheck();
        initializeSignedPreKeyCheck();
        initializePeriodicTasks();
        initializeCircumvention();
        initializeWebRtc();
        initializePendingMessages();
        initializeUnidentifiedDeliveryAbilityRefresh();
        initializeBlobProvider();
        initializeCameraX();
    }

  private static class ProviderInitializationException extends RuntimeException {
  }

}
