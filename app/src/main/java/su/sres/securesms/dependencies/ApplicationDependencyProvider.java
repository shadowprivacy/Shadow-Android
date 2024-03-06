package su.sres.securesms.dependencies;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;

import su.sres.securesms.BuildConfig;
import su.sres.securesms.components.TypingStatusRepository;
import su.sres.securesms.components.TypingStatusSender;
import su.sres.securesms.crypto.ReentrantSessionLock;
import su.sres.securesms.database.DatabaseObserver;
import su.sres.securesms.database.JobDatabase;
import su.sres.securesms.database.PendingRetryReceiptCache;
import su.sres.securesms.jobmanager.impl.FactoryJobPredicate;
import su.sres.securesms.jobs.GroupCallUpdateSendJob;
import su.sres.securesms.jobs.MarkerJob;
import su.sres.securesms.jobs.PushDecryptMessageJob;
import su.sres.securesms.jobs.PushGroupSendJob;
import su.sres.securesms.jobs.PushMediaSendJob;
import su.sres.securesms.jobs.PushProcessMessageJob;
import su.sres.securesms.jobs.PushTextSendJob;
import su.sres.securesms.jobs.ReactionSendJob;
import su.sres.securesms.jobs.TypingSendJob;
import su.sres.securesms.messages.IncomingMessageObserver;
import su.sres.securesms.messages.IncomingMessageProcessor;
import su.sres.securesms.crypto.storage.SignalProtocolStoreImpl;
import su.sres.securesms.messages.BackgroundMessageRetriever;
import su.sres.securesms.jobmanager.JobManager;
import su.sres.securesms.jobmanager.JobMigrator;
import su.sres.securesms.jobmanager.impl.JsonDataSerializer;
import su.sres.securesms.jobs.FastJobStorage;
import su.sres.securesms.jobs.JobManagerFactories;
import su.sres.core.util.logging.Log;
import su.sres.securesms.megaphone.MegaphoneRepository;
import su.sres.securesms.net.SignalWebSocketHealthMonitor;
import su.sres.securesms.notifications.MessageNotifier;
import su.sres.securesms.notifications.OptimizedMessageNotifier;
import su.sres.securesms.payments.MobileCoinConfig;
import su.sres.securesms.payments.Payments;
import su.sres.securesms.push.SecurityEventListener;
import su.sres.securesms.push.SignalServiceNetworkAccess;
import su.sres.securesms.recipients.LiveRecipientCache;
import su.sres.securesms.revealable.ViewOnceMessageManager;
import su.sres.securesms.service.ExpiringMessageManager;
import su.sres.securesms.service.PendingRetryReceiptManager;
import su.sres.securesms.service.TrimThreadsByDateManager;
import su.sres.securesms.service.webrtc.SignalCallManager;
import su.sres.securesms.util.AlarmSleepTimer;
import su.sres.securesms.util.ByteUnit;
import su.sres.securesms.util.EarlyMessageCache;
import su.sres.securesms.util.FeatureFlags;
import su.sres.securesms.util.FrameRateTracker;
import su.sres.securesms.util.TextSecurePreferences;

import org.whispersystems.libsignal.util.guava.Optional;

import su.sres.core.util.concurrent.SignalExecutors;
import su.sres.signalservice.api.SignalServiceAccountManager;
import su.sres.signalservice.api.SignalServiceMessageReceiver;
import su.sres.signalservice.api.SignalServiceMessageSender;
import su.sres.signalservice.api.SignalWebSocket;
import su.sres.signalservice.api.groupsv2.ClientZkOperations;
import su.sres.signalservice.api.groupsv2.GroupsV2Operations;
import su.sres.signalservice.api.util.CredentialsProvider;
import su.sres.signalservice.api.util.SleepTimer;
import su.sres.signalservice.api.util.UptimeSleepTimer;
import su.sres.signalservice.api.websocket.WebSocketFactory;
import su.sres.signalservice.internal.websocket.WebSocketConnection;

import java.util.UUID;

/**
 * Implementation of {@link ApplicationDependencies.Provider} that provides real app dependencies.
 */
public class ApplicationDependencyProvider implements ApplicationDependencies.Provider {

  private final Application context;

  public ApplicationDependencyProvider(@NonNull Application context) {
    this.context = context;
  }

  private @NonNull
  ClientZkOperations provideClientZkOperations() {
    return ClientZkOperations.create(provideSignalServiceNetworkAccess().getConfiguration());
  }

  @Override
  public @NonNull
  GroupsV2Operations provideGroupsV2Operations() {
    return new GroupsV2Operations(provideClientZkOperations());
  }

  @Override
  public @NonNull SignalServiceAccountManager provideSignalServiceAccountManager() {
    return new SignalServiceAccountManager(provideSignalServiceNetworkAccess().getConfiguration(),
                                           new DynamicCredentialsProvider(context),
                                           BuildConfig.SIGNAL_AGENT,
                                           provideGroupsV2Operations(),
                                           FeatureFlags.okHttpAutomaticRetry());
  }

  @Override
  public @NonNull SignalServiceMessageSender provideSignalServiceMessageSender(@NonNull SignalWebSocket signalWebSocket) {
    return new SignalServiceMessageSender(provideSignalServiceNetworkAccess().getConfiguration(),
                                          new DynamicCredentialsProvider(context),
                                          new SignalProtocolStoreImpl(context),
                                          ReentrantSessionLock.INSTANCE,
                                          BuildConfig.SIGNAL_AGENT,
                                          FeatureFlags.attachmentsV3(),
                                          signalWebSocket,
                                          Optional.of(new SecurityEventListener(context)),
                                          provideClientZkOperations().getProfileOperations(),
                                          SignalExecutors.newCachedBoundedExecutor("shadow-messages", 1, 16),
                                          ByteUnit.KILOBYTES.toBytes(512),
                                          FeatureFlags.okHttpAutomaticRetry());
  }

  @Override
  public @NonNull SignalServiceMessageReceiver provideSignalServiceMessageReceiver() {
    return new SignalServiceMessageReceiver(provideSignalServiceNetworkAccess().getConfiguration(),
                                            new DynamicCredentialsProvider(context),
                                            BuildConfig.SIGNAL_AGENT,
                                            provideClientZkOperations().getProfileOperations(),
                                            FeatureFlags.okHttpAutomaticRetry());
  }

  @Override
  public @NonNull SignalServiceNetworkAccess provideSignalServiceNetworkAccess() {
    return new SignalServiceNetworkAccess(context);
  }

  @Override
  public @NonNull IncomingMessageProcessor provideIncomingMessageProcessor() {
    return new IncomingMessageProcessor(context);
  }

  @Override
  public @NonNull BackgroundMessageRetriever provideBackgroundMessageRetriever() {
    return new BackgroundMessageRetriever();
  }

  @Override
  public @NonNull LiveRecipientCache provideRecipientCache() {
    return new LiveRecipientCache(context);
  }

  @Override
  public @NonNull JobManager provideJobManager() {
    JobManager.Configuration config = new JobManager.Configuration.Builder()
        .setDataSerializer(new JsonDataSerializer())
        .setJobFactories(JobManagerFactories.getJobFactories(context))
        .setConstraintFactories(JobManagerFactories.getConstraintFactories(context))
        .setConstraintObservers(JobManagerFactories.getConstraintObservers(context))
        .setJobStorage(new FastJobStorage(JobDatabase.getInstance(context)))
        .setJobMigrator(new JobMigrator(TextSecurePreferences.getJobManagerVersion(context), JobManager.CURRENT_VERSION, JobManagerFactories.getJobMigrations(context)))
        .addReservedJobRunner(new FactoryJobPredicate(PushDecryptMessageJob.KEY, PushProcessMessageJob.KEY, MarkerJob.KEY))
        .addReservedJobRunner(new FactoryJobPredicate(PushTextSendJob.KEY, PushMediaSendJob.KEY, PushGroupSendJob.KEY, ReactionSendJob.KEY, TypingSendJob.KEY, GroupCallUpdateSendJob.KEY))
        .build();
    return new JobManager(context, config);
  }

  @Override
  public @NonNull FrameRateTracker provideFrameRateTracker() {
    return new FrameRateTracker(context);
  }

  @Override
  public @NonNull MegaphoneRepository provideMegaphoneRepository() {
    return new MegaphoneRepository(context);
  }

  @Override
  public @NonNull EarlyMessageCache provideEarlyMessageCache() {
    return new EarlyMessageCache();
  }

  @Override
  public @NonNull MessageNotifier provideMessageNotifier() {
    return new OptimizedMessageNotifier(context);
  }

  @Override
  public @NonNull IncomingMessageObserver provideIncomingMessageObserver() {
    return new IncomingMessageObserver(context);
  }

  @Override
  public @NonNull TrimThreadsByDateManager provideTrimThreadsByDateManager() {
    return new TrimThreadsByDateManager(context);
  }

  @Override
  public @NonNull ViewOnceMessageManager provideViewOnceMessageManager() {
    return new ViewOnceMessageManager(context);
  }

  @Override
  public @NonNull ExpiringMessageManager provideExpiringMessageManager() {
    return new ExpiringMessageManager(context);
  }

  @Override
  public @NonNull TypingStatusRepository provideTypingStatusRepository() {
    return new TypingStatusRepository();
  }

  @Override
  public @NonNull TypingStatusSender provideTypingStatusSender() {
    return new TypingStatusSender();
  }

  @Override
  public @NonNull DatabaseObserver provideDatabaseObserver() {
    return new DatabaseObserver(context);
  }

  @SuppressWarnings("ConstantConditions")
  @Override
  public @NonNull Payments providePayments(@NonNull SignalServiceAccountManager signalServiceAccountManager) {
    MobileCoinConfig network;

    if (BuildConfig.MOBILE_COIN_ENVIRONMENT.equals("mainnet")) network = MobileCoinConfig.getMainNet(signalServiceAccountManager);
    else if (BuildConfig.MOBILE_COIN_ENVIRONMENT.equals("testnet")) network = MobileCoinConfig.getTestNet(signalServiceAccountManager);
    else throw new AssertionError("Unknown network " + BuildConfig.MOBILE_COIN_ENVIRONMENT);

    return new Payments(network);
  }

  @Override
  public @NonNull SignalCallManager provideSignalCallManager() {
    return new SignalCallManager(context);
  }

  @Override
  public @NonNull PendingRetryReceiptManager providePendingRetryReceiptManager() {
    return new PendingRetryReceiptManager(context);
  }

  @Override
  public @NonNull PendingRetryReceiptCache providePendingRetryReceiptCache() {
    return new PendingRetryReceiptCache(context);
  }

  @Override
  public @NonNull SignalWebSocket provideSignalWebSocket() {
    SleepTimer                   sleepTimer      = TextSecurePreferences.isFcmDisabled(context) ? new AlarmSleepTimer(context) : new UptimeSleepTimer();
    SignalWebSocketHealthMonitor healthMonitor   = new SignalWebSocketHealthMonitor(context, sleepTimer);
    SignalWebSocket              signalWebSocket = new SignalWebSocket(provideWebSocketFactory(healthMonitor));

    healthMonitor.monitor(signalWebSocket);

    return signalWebSocket;
  }

  private @NonNull WebSocketFactory provideWebSocketFactory(@NonNull SignalWebSocketHealthMonitor healthMonitor) {
    return new WebSocketFactory() {
      @Override
      public WebSocketConnection createWebSocket() {
        return new WebSocketConnection("normal",
                                       provideSignalServiceNetworkAccess().getConfiguration(),
                                       Optional.of(new DynamicCredentialsProvider(context)),
                                       BuildConfig.SIGNAL_AGENT,
                                       healthMonitor);
      }

      @Override
      public WebSocketConnection createUnidentifiedWebSocket() {
        return new WebSocketConnection("unidentified",
                                       provideSignalServiceNetworkAccess().getConfiguration(),
                                       Optional.absent(),
                                       BuildConfig.SIGNAL_AGENT,
                                       healthMonitor);
      }
    };
  }

  private static class DynamicCredentialsProvider implements CredentialsProvider {

    private final Context context;

    private DynamicCredentialsProvider(Context context) {
      this.context = context.getApplicationContext();
    }

    @Override
    public UUID getUuid() {
      return TextSecurePreferences.getLocalUuid(context);
    }

    @Override
    public String getUserLogin() {
      return TextSecurePreferences.getLocalNumber(context);
    }

    @Override
    public String getPassword() {
      return TextSecurePreferences.getPushServerPassword(context);
    }
  }
}