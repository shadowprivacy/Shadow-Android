package su.sres.securesms.dependencies;

import android.app.Application;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import org.signal.zkgroup.receipts.ClientZkReceiptOperations;

import okhttp3.OkHttpClient;
import su.sres.core.util.concurrent.DeadlockDetector;
import su.sres.securesms.components.TypingStatusRepository;
import su.sres.securesms.components.TypingStatusSender;
import su.sres.securesms.crypto.storage.SignalSenderKeyStore;
import su.sres.securesms.crypto.storage.TextSecureIdentityKeyStore;
import su.sres.securesms.crypto.storage.TextSecurePreKeyStore;
import su.sres.securesms.crypto.storage.TextSecureSessionStore;
import su.sres.securesms.database.DatabaseObserver;
import su.sres.securesms.database.PendingRetryReceiptCache;
import su.sres.securesms.messages.BackgroundMessageRetriever;
import su.sres.securesms.messages.IncomingMessageObserver;
import su.sres.securesms.messages.IncomingMessageProcessor;
import su.sres.securesms.groups.GroupsV2Authorization;
import su.sres.securesms.groups.GroupsV2AuthorizationMemoryValueCache;
import su.sres.securesms.groups.v2.processing.GroupsV2StateProcessor;
import su.sres.securesms.jobmanager.JobManager;
import su.sres.securesms.keyvalue.KeyValueStore;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.securesms.megaphone.MegaphoneRepository;
import su.sres.securesms.net.StandardUserAgentInterceptor;
import su.sres.securesms.notifications.MessageNotifier;
import su.sres.securesms.payments.Payments;
import su.sres.securesms.push.SignalServiceNetworkAccess;
import su.sres.securesms.recipients.LiveRecipientCache;
import su.sres.securesms.revealable.ViewOnceMessageManager;
import su.sres.securesms.service.ExpiringMessageManager;
import su.sres.securesms.service.PendingRetryReceiptManager;
import su.sres.securesms.service.TrimThreadsByDateManager;
import su.sres.securesms.service.webrtc.SignalCallManager;
import su.sres.securesms.shakereport.ShakeToReport;
import su.sres.securesms.util.AppForegroundObserver;
import su.sres.securesms.util.EarlyMessageCache;
import su.sres.securesms.util.FeatureFlags;
import su.sres.securesms.util.FrameRateTracker;
import su.sres.securesms.video.exo.GiphyMp4Cache;
import su.sres.securesms.video.exo.SimpleExoPlayerPool;
import su.sres.securesms.webrtc.audio.AudioManagerCompat;
import su.sres.signalservice.api.SignalServiceAccountManager;
import su.sres.signalservice.api.SignalServiceMessageReceiver;
import su.sres.signalservice.api.SignalServiceMessageSender;
import su.sres.signalservice.api.SignalWebSocket;
import su.sres.signalservice.api.groupsv2.GroupsV2Operations;
import su.sres.signalservice.api.services.DonationsService;

/**
 * Location for storing and retrieving application-scoped singletons. Users must call
 * {@link #networkIndependentProviderInit(Application, NetworkIndependentProvider)} and
 * {@link #networkDependentProviderInit(Provider)} before using any of the methods, preferably early on in
 * {@link Application#onCreate()}.
 * <p>
 * All future application-scoped singletons should be written as normal objects, then placed here
 * to manage their singleton-ness.
 */
public class ApplicationDependencies {

  private static final Object LOCK                    = new Object();
  private static final Object NI_LOCK                 = new Object();
  private static final Object FRAME_RATE_TRACKER_LOCK = new Object();
  private static final Object JOB_MANAGER_LOCK        = new Object();

  private static Application                application;
  private static Provider                   provider;
  private static NetworkIndependentProvider networkIndependentProvider;

  private static AppForegroundObserver appForegroundObserver;

  private static volatile SignalServiceAccountManager  accountManager;
  private static volatile SignalServiceMessageSender   messageSender;
  private static volatile SignalServiceMessageReceiver messageReceiver;
  private static volatile IncomingMessageObserver      incomingMessageObserver;
  private static volatile IncomingMessageProcessor     incomingMessageProcessor;
  private static volatile BackgroundMessageRetriever   backgroundMessageRetriever;
  private static volatile LiveRecipientCache           recipientCache;
  private static volatile JobManager                   jobManager;
  private static volatile FrameRateTracker             frameRateTracker;
  private static volatile MegaphoneRepository          megaphoneRepository;
  private static volatile GroupsV2Authorization        groupsV2Authorization;
  private static volatile GroupsV2StateProcessor       groupsV2StateProcessor;
  private static volatile GroupsV2Operations           groupsV2Operations;
  private static volatile EarlyMessageCache            earlyMessageCache;
  private static volatile TypingStatusRepository       typingStatusRepository;
  private static volatile TypingStatusSender           typingStatusSender;
  private static volatile DatabaseObserver             databaseObserver;
  private static volatile TrimThreadsByDateManager     trimThreadsByDateManager;
  private static volatile ViewOnceMessageManager       viewOnceMessageManager;
  private static volatile ExpiringMessageManager       expiringMessageManager;
  private static volatile Payments                     payments;
  private static volatile ShakeToReport                shakeToReport;
  private static volatile SignalCallManager            signalCallManager;
  private static volatile OkHttpClient                 okHttpClient;
  private static volatile PendingRetryReceiptManager   pendingRetryReceiptManager;
  private static volatile PendingRetryReceiptCache     pendingRetryReceiptCache;
  private static volatile SignalWebSocket              signalWebSocket;
  private static volatile MessageNotifier              messageNotifier;
  private static volatile TextSecureIdentityKeyStore   identityStore;
  private static volatile TextSecureSessionStore       sessionStore;
  private static volatile TextSecurePreKeyStore        preKeyStore;
  private static volatile SignalSenderKeyStore         senderKeyStore;
  private static volatile GiphyMp4Cache                giphyMp4Cache;
  private static volatile SimpleExoPlayerPool          exoPlayerPool;
  private static volatile AudioManagerCompat           audioManagerCompat;
  private static volatile DonationsService             donationsService;
  private static volatile DeadlockDetector             deadlockDetector;
  private static volatile ClientZkReceiptOperations    clientZkReceiptOperations;
  private static volatile KeyValueStore                keyValueStore;

  public static void networkIndependentProviderInit(@NonNull Application application, @NonNull NetworkIndependentProvider networkIndependentProvider) {
    synchronized (NI_LOCK) {
      if (ApplicationDependencies.application != null || ApplicationDependencies.networkIndependentProvider != null) {
        throw new IllegalStateException("Already initialized!");
      }
    }

    ApplicationDependencies.application                = application;
    ApplicationDependencies.networkIndependentProvider = networkIndependentProvider;
    ApplicationDependencies.appForegroundObserver      = networkIndependentProvider.provideAppForegroundObserver();

    ApplicationDependencies.appForegroundObserver.begin();

  }

  @MainThread
  public static void networkDependentProviderInit(@NonNull Provider provider) {

    synchronized (LOCK) {
      if (ApplicationDependencies.provider != null) {
        throw new IllegalStateException("Already initialized!");
      }
    }

    ApplicationDependencies.provider = provider;
  }

  @VisibleForTesting
  public static boolean isInitialized() {
    return ApplicationDependencies.application != null;
  }

  public static @NonNull Application getApplication() {
    return application;
  }

  public static @NonNull SignalServiceAccountManager getSignalServiceAccountManager() {
    SignalServiceAccountManager local = accountManager;

    if (local != null) {
      return local;
    }

    synchronized (LOCK) {
      if (accountManager == null) {
        accountManager = provider.provideSignalServiceAccountManager();
      }
      return accountManager;
    }
  }

  public static @NonNull GroupsV2Authorization getGroupsV2Authorization() {
    if (groupsV2Authorization == null) {
      synchronized (LOCK) {
        if (groupsV2Authorization == null) {
          GroupsV2Authorization.ValueCache authCache = new GroupsV2AuthorizationMemoryValueCache(SignalStore.groupsV2AuthorizationCache());
          groupsV2Authorization = new GroupsV2Authorization(getSignalServiceAccountManager().getGroupsV2Api(), authCache);
        }
      }
    }
    return groupsV2Authorization;
  }

  public static @NonNull GroupsV2Operations getGroupsV2Operations() {
    if (groupsV2Operations == null) {
      synchronized (LOCK) {
        if (groupsV2Operations == null) {
          groupsV2Operations = provider.provideGroupsV2Operations();
        }
      }
    }
    return groupsV2Operations;
  }

  public static @NonNull GroupsV2StateProcessor getGroupsV2StateProcessor() {
    if (groupsV2StateProcessor == null) {
      synchronized (LOCK) {
        if (groupsV2StateProcessor == null) {
          groupsV2StateProcessor = new GroupsV2StateProcessor(application);
        }
      }
    }
    return groupsV2StateProcessor;
  }

  public static @NonNull SignalServiceMessageSender getSignalServiceMessageSender() {

    SignalServiceMessageSender local = messageSender;

    if (local != null) {
      return local;
    }

    synchronized (LOCK) {
      if (messageSender == null) {
        messageSender = provider.provideSignalServiceMessageSender(getSignalWebSocket());
      } else {
        messageSender.update(FeatureFlags.attachmentsV3());
      }
      return messageSender;
    }
  }

  public static @NonNull SignalServiceMessageReceiver getSignalServiceMessageReceiver() {

    synchronized (LOCK) {
      if (messageReceiver == null) {
        messageReceiver = provider.provideSignalServiceMessageReceiver();
      }
      return messageReceiver;
    }
  }

  public static void resetSignalServiceMessageReceiver() {
    synchronized (LOCK) {
      messageReceiver = null;
    }
  }

  public static void closeConnections() {
    synchronized (LOCK) {

      if (incomingMessageObserver != null) {
        incomingMessageObserver.terminateAsync();
      }

      if (messageSender != null) {
        messageSender.cancelInFlightRequests();
      }

      incomingMessageObserver = null;
      messageReceiver         = null;
      accountManager          = null;
      messageSender           = null;
    }
  }

  public static void resetNetworkConnectionsAfterProxyChange() {
    synchronized (LOCK) {
      closeConnections();
    }
  }

  public static @NonNull SignalServiceNetworkAccess getSignalServiceNetworkAccess() {
    return provider.provideSignalServiceNetworkAccess();
  }

  public static @NonNull IncomingMessageProcessor getIncomingMessageProcessor() {
    if (incomingMessageProcessor == null) {
      synchronized (LOCK) {
        if (incomingMessageProcessor == null) {
          incomingMessageProcessor = provider.provideIncomingMessageProcessor();
        }
      }
    }
    return incomingMessageProcessor;
  }

  public static @NonNull BackgroundMessageRetriever getBackgroundMessageRetriever() {
    if (backgroundMessageRetriever == null) {
      synchronized (LOCK) {
        if (backgroundMessageRetriever == null) {
          backgroundMessageRetriever = provider.provideBackgroundMessageRetriever();
        }
      }
    }
    return backgroundMessageRetriever;
  }

  public static @NonNull LiveRecipientCache getRecipientCache() {
    if (recipientCache == null) {
      synchronized (LOCK) {
        if (recipientCache == null) {
          recipientCache = provider.provideRecipientCache();
        }
      }
    }
    return recipientCache;
  }

  public static @NonNull JobManager getJobManager() {
    if (jobManager == null) {
      synchronized (JOB_MANAGER_LOCK) {
        if (jobManager == null) {
          jobManager = provider.provideJobManager();
        }
      }
    }
    return jobManager;
  }

  public static @NonNull FrameRateTracker getFrameRateTracker() {
    if (frameRateTracker == null) {
      synchronized (FRAME_RATE_TRACKER_LOCK) {
        if (frameRateTracker == null) {
          frameRateTracker = provider.provideFrameRateTracker();
        }
      }
    }
    return frameRateTracker;
  }

  public static @NonNull TextSecureIdentityKeyStore getIdentityStore() {
    if (identityStore == null) {
      synchronized (NI_LOCK) {
        if (identityStore == null) {
          identityStore = networkIndependentProvider.provideIdentityStore();
        }
      }
    }
    return identityStore;
  }

  public static @NonNull TextSecureSessionStore getSessionStore() {
    if (sessionStore == null) {
      synchronized (NI_LOCK) {
        if (sessionStore == null) {
          sessionStore = networkIndependentProvider.provideSessionStore();
        }
      }
    }
    return sessionStore;
  }

  public static @NonNull TextSecurePreKeyStore getPreKeyStore() {
    if (preKeyStore == null) {
      synchronized (NI_LOCK) {
        if (preKeyStore == null) {
          preKeyStore = networkIndependentProvider.providePreKeyStore();
        }
      }
    }
    return preKeyStore;
  }

  public static @NonNull SignalSenderKeyStore getSenderKeyStore() {
    if (senderKeyStore == null) {
      synchronized (NI_LOCK) {
        if (senderKeyStore == null) {
          senderKeyStore = networkIndependentProvider.provideSenderKeyStore();
        }
      }
    }
    return senderKeyStore;
  }

  public static @NonNull GiphyMp4Cache getGiphyMp4Cache() {
    if (giphyMp4Cache == null) {
      synchronized (NI_LOCK) {
        if (giphyMp4Cache == null) {
          giphyMp4Cache = networkIndependentProvider.provideGiphyMp4Cache();
        }
      }
    }
    return giphyMp4Cache;
  }

  public static @NonNull MegaphoneRepository getMegaphoneRepository() {
    if (megaphoneRepository == null) {
      synchronized (LOCK) {
        if (megaphoneRepository == null) {
          megaphoneRepository = provider.provideMegaphoneRepository();
        }
      }
    }
    return megaphoneRepository;
  }

  public static @NonNull EarlyMessageCache getEarlyMessageCache() {
    if (earlyMessageCache == null) {
      synchronized (LOCK) {
        if (earlyMessageCache == null) {
          earlyMessageCache = provider.provideEarlyMessageCache();
        }
      }
    }
    return earlyMessageCache;
  }

  public static @NonNull MessageNotifier getMessageNotifier() {
    if (messageNotifier == null) {
      synchronized (LOCK) {
        if (messageNotifier == null) {
          messageNotifier = provider.provideMessageNotifier();
        }
      }
    }
    return messageNotifier;
  }

  public static @NonNull IncomingMessageObserver getIncomingMessageObserver() {
    IncomingMessageObserver local = incomingMessageObserver;

    if (local != null) {
      return local;
    }

    synchronized (LOCK) {
      if (incomingMessageObserver == null) {
        incomingMessageObserver = provider.provideIncomingMessageObserver();
      }
      return incomingMessageObserver;
    }
  }

  public static @NonNull TrimThreadsByDateManager getTrimThreadsByDateManager() {
    if (trimThreadsByDateManager == null) {
      synchronized (LOCK) {
        if (trimThreadsByDateManager == null) {
          trimThreadsByDateManager = provider.provideTrimThreadsByDateManager();
        }
      }
    }

    return trimThreadsByDateManager;
  }

  public static @NonNull ViewOnceMessageManager getViewOnceMessageManager() {
    if (viewOnceMessageManager == null) {
      synchronized (LOCK) {
        if (viewOnceMessageManager == null) {
          viewOnceMessageManager = provider.provideViewOnceMessageManager();
        }
      }
    }

    return viewOnceMessageManager;
  }

  public static @NonNull PendingRetryReceiptManager getPendingRetryReceiptManager() {
    if (pendingRetryReceiptManager == null) {
      synchronized (LOCK) {
        if (pendingRetryReceiptManager == null) {
          pendingRetryReceiptManager = provider.providePendingRetryReceiptManager();
        }
      }
    }

    return pendingRetryReceiptManager;
  }

  public static @NonNull ExpiringMessageManager getExpiringMessageManager() {
    if (expiringMessageManager == null) {
      synchronized (LOCK) {
        if (expiringMessageManager == null) {
          expiringMessageManager = provider.provideExpiringMessageManager();
        }
      }
    }

    return expiringMessageManager;
  }

  public static TypingStatusRepository getTypingStatusRepository() {

    if (typingStatusRepository == null) {
      synchronized (LOCK) {
        if (typingStatusRepository == null) {
          typingStatusRepository = provider.provideTypingStatusRepository();
        }
      }
    }

    return typingStatusRepository;
  }

  public static TypingStatusSender getTypingStatusSender() {

    if (typingStatusSender == null) {
      synchronized (LOCK) {
        if (typingStatusSender == null) {
          typingStatusSender = provider.provideTypingStatusSender();
        }
      }
    }

    return typingStatusSender;
  }

  public static @NonNull DatabaseObserver getDatabaseObserver() {
    if (databaseObserver == null) {
      synchronized (LOCK) {
        if (databaseObserver == null) {
          databaseObserver = provider.provideDatabaseObserver();
        }
      }
    }

    return databaseObserver;
  }

  public static @NonNull Payments getPayments() {
    if (payments == null) {
      synchronized (LOCK) {
        if (payments == null) {
          payments = provider.providePayments(getSignalServiceAccountManager());
        }
      }
    }

    return payments;
  }

  public static @NonNull ShakeToReport getShakeToReport() {
    if (shakeToReport == null) {
      synchronized (NI_LOCK) {
        if (shakeToReport == null) {
          shakeToReport = networkIndependentProvider.provideShakeToReport();
        }
      }
    }

    return shakeToReport;
  }

  public static @NonNull SignalCallManager getSignalCallManager() {
    if (signalCallManager == null) {
      synchronized (LOCK) {
        if (signalCallManager == null) {
          signalCallManager = provider.provideSignalCallManager();
        }
      }
    }

    return signalCallManager;
  }

  public static @NonNull OkHttpClient getOkHttpClient() {
    if (okHttpClient == null) {
      synchronized (LOCK) {
        if (okHttpClient == null) {
          okHttpClient = new OkHttpClient.Builder()
              .addInterceptor(new StandardUserAgentInterceptor())
              .dns(SignalServiceNetworkAccess.DNS)
              .build();
        }
      }
    }

    return okHttpClient;
  }

  public static @NonNull AppForegroundObserver getAppForegroundObserver() {
    return appForegroundObserver;
  }

  public static @NonNull PendingRetryReceiptCache getPendingRetryReceiptCache() {
    if (pendingRetryReceiptCache == null) {
      synchronized (LOCK) {
        if (pendingRetryReceiptCache == null) {
          pendingRetryReceiptCache = provider.providePendingRetryReceiptCache();
        }
      }
    }

    return pendingRetryReceiptCache;
  }

  public static @NonNull SignalWebSocket getSignalWebSocket() {
    if (signalWebSocket == null) {
      synchronized (LOCK) {
        if (signalWebSocket == null) {
          signalWebSocket = provider.provideSignalWebSocket();
        }
      }
    }
    return signalWebSocket;
  }

  public static @NonNull SimpleExoPlayerPool getExoPlayerPool() {
    if (exoPlayerPool == null) {
      synchronized (NI_LOCK) {
        if (exoPlayerPool == null) {
          exoPlayerPool = networkIndependentProvider.provideExoPlayerPool();
        }
      }
    }
    return exoPlayerPool;
  }

  public static @NonNull AudioManagerCompat getAndroidCallAudioManager() {
    if (audioManagerCompat == null) {
      synchronized (LOCK) {
        if (audioManagerCompat == null) {
          audioManagerCompat = provider.provideAndroidCallAudioManager();
        }
      }
    }
    return audioManagerCompat;
  }

  public static @NonNull DonationsService getDonationsService() {
    if (donationsService == null) {
      synchronized (LOCK) {
        if (donationsService == null) {
          donationsService = provider.provideDonationsService();
        }
      }
    }
    return donationsService;
  }

  public static @NonNull ClientZkReceiptOperations getClientZkReceiptOperations() {
    if (clientZkReceiptOperations == null) {
      synchronized (LOCK) {
        if (clientZkReceiptOperations == null) {
          clientZkReceiptOperations = provider.provideClientZkReceiptOperations();
        }
      }
    }
    return clientZkReceiptOperations;
  }

  public static @NonNull DeadlockDetector getDeadlockDetector() {
    if (deadlockDetector == null) {
      synchronized (NI_LOCK) {
        if (deadlockDetector == null) {
          deadlockDetector = networkIndependentProvider.provideDeadlockDetector();
        }
      }
    }
    return deadlockDetector;
  }

  public interface Provider {
    @NonNull
    GroupsV2Operations provideGroupsV2Operations();

    @NonNull SignalServiceAccountManager provideSignalServiceAccountManager();

    @NonNull SignalServiceMessageSender provideSignalServiceMessageSender(@NonNull SignalWebSocket signalWebSocket);

    @NonNull SignalServiceMessageReceiver provideSignalServiceMessageReceiver();

    @NonNull SignalServiceNetworkAccess provideSignalServiceNetworkAccess();

    @NonNull IncomingMessageProcessor provideIncomingMessageProcessor();

    @NonNull BackgroundMessageRetriever provideBackgroundMessageRetriever();

    @NonNull LiveRecipientCache provideRecipientCache();

    @NonNull JobManager provideJobManager();

    @NonNull FrameRateTracker provideFrameRateTracker();

    @NonNull MegaphoneRepository provideMegaphoneRepository();

    @NonNull EarlyMessageCache provideEarlyMessageCache();

    @NonNull MessageNotifier provideMessageNotifier();

    @NonNull IncomingMessageObserver provideIncomingMessageObserver();

    @NonNull TrimThreadsByDateManager provideTrimThreadsByDateManager();

    @NonNull ViewOnceMessageManager provideViewOnceMessageManager();

    @NonNull ExpiringMessageManager provideExpiringMessageManager();

    @NonNull TypingStatusRepository provideTypingStatusRepository();

    @NonNull TypingStatusSender provideTypingStatusSender();

    @NonNull DatabaseObserver provideDatabaseObserver();

    @NonNull Payments providePayments(@NonNull SignalServiceAccountManager signalServiceAccountManager);

    @NonNull
    SignalCallManager provideSignalCallManager();

    @NonNull PendingRetryReceiptManager providePendingRetryReceiptManager();

    @NonNull PendingRetryReceiptCache providePendingRetryReceiptCache();

    @NonNull SignalWebSocket provideSignalWebSocket();

    @NonNull AudioManagerCompat provideAndroidCallAudioManager();

    @NonNull DonationsService provideDonationsService();

    @NonNull ClientZkReceiptOperations provideClientZkReceiptOperations();
  }

  public interface NetworkIndependentProvider {

    @NonNull TextSecureIdentityKeyStore provideIdentityStore();

    @NonNull TextSecureSessionStore provideSessionStore();

    @NonNull TextSecurePreKeyStore providePreKeyStore();

    @NonNull SignalSenderKeyStore provideSenderKeyStore();

    @NonNull GiphyMp4Cache provideGiphyMp4Cache();

    @NonNull SimpleExoPlayerPool provideExoPlayerPool();

    @NonNull
    ShakeToReport provideShakeToReport();

    @NonNull AppForegroundObserver provideAppForegroundObserver();

    @NonNull DeadlockDetector provideDeadlockDetector();

  }
}