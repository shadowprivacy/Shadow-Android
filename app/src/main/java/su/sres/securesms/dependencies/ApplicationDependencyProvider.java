package su.sres.securesms.dependencies;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;

import org.greenrobot.eventbus.EventBus;
import su.sres.securesms.BuildConfig;
import su.sres.securesms.messages.IncomingMessageProcessor;
import su.sres.securesms.crypto.storage.SignalProtocolStoreImpl;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.events.ReminderUpdateEvent;
import su.sres.securesms.messages.BackgroundMessageRetriever;
import su.sres.securesms.jobmanager.JobManager;
import su.sres.securesms.jobmanager.JobMigrator;
import su.sres.securesms.jobmanager.impl.JsonDataSerializer;
import su.sres.securesms.jobs.FastJobStorage;
import su.sres.securesms.jobs.JobManagerFactories;
import su.sres.securesms.logging.Log;
import su.sres.securesms.megaphone.MegaphoneRepository;
import su.sres.securesms.messages.InitialMessageRetriever;
import su.sres.securesms.notifications.DefaultMessageNotifier;
import su.sres.securesms.notifications.MessageNotifier;
import su.sres.securesms.notifications.OptimizedMessageNotifier;
import su.sres.securesms.push.SecurityEventListener;
import su.sres.securesms.push.SignalServiceNetworkAccess;
import su.sres.securesms.recipients.LiveRecipientCache;
import su.sres.securesms.messages.IncomingMessageObserver;
import su.sres.securesms.util.AlarmSleepTimer;
import su.sres.securesms.util.EarlyMessageCache;
import su.sres.securesms.util.FeatureFlags;
import su.sres.securesms.util.FrameRateTracker;
import su.sres.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.util.guava.Optional;
import su.sres.signalservice.api.SignalServiceAccountManager;
import su.sres.signalservice.api.SignalServiceMessageReceiver;
import su.sres.signalservice.api.SignalServiceMessageSender;
import su.sres.signalservice.api.groupsv2.ClientZkOperations;
import su.sres.signalservice.api.groupsv2.GroupsV2Operations;
import su.sres.signalservice.api.util.CredentialsProvider;
import su.sres.signalservice.api.util.SleepTimer;
import su.sres.signalservice.api.util.UptimeSleepTimer;
import su.sres.signalservice.api.websocket.ConnectivityListener;

import java.util.UUID;

/**
 * Implementation of {@link ApplicationDependencies.Provider} that provides real app dependencies.
 */
public class ApplicationDependencyProvider implements ApplicationDependencies.Provider {

    private static final String TAG = Log.tag(ApplicationDependencyProvider.class);

    private final Application                context;
    private final SignalServiceNetworkAccess networkAccess;

    public ApplicationDependencyProvider(@NonNull Application context, @NonNull SignalServiceNetworkAccess networkAccess) {
        this.context       = context;
        this.networkAccess = networkAccess;
    }

    private @NonNull
    ClientZkOperations provideClientZkOperations() {
        return ClientZkOperations.create(networkAccess.getConfiguration(context));
    }

    @Override
    public @NonNull
    GroupsV2Operations provideGroupsV2Operations() {
        return new GroupsV2Operations(provideClientZkOperations());
    }

    @Override
    public @NonNull SignalServiceAccountManager provideSignalServiceAccountManager() {
        networkAccess.renewConfiguration(context);
        return new SignalServiceAccountManager(networkAccess.getConfiguration(context),
                new DynamicCredentialsProvider(context),
                BuildConfig.SIGNAL_AGENT,
                provideGroupsV2Operations());
    }

    @Override
    public @NonNull SignalServiceMessageSender provideSignalServiceMessageSender() {
        networkAccess.renewConfiguration(context);
        return new SignalServiceMessageSender(networkAccess.getConfiguration(context),
                new DynamicCredentialsProvider(context),
                new SignalProtocolStoreImpl(context),
                BuildConfig.SIGNAL_AGENT,
                TextSecurePreferences.isMultiDevice(context),
                FeatureFlags.attachmentsV3(),
                Optional.fromNullable(IncomingMessageObserver.getPipe()),
                Optional.fromNullable(IncomingMessageObserver.getUnidentifiedPipe()),
                Optional.of(new SecurityEventListener(context)),
                provideClientZkOperations().getProfileOperations());
    }

    @Override
    public @NonNull SignalServiceMessageReceiver provideSignalServiceMessageReceiver() {
        SleepTimer sleepTimer = TextSecurePreferences.isFcmDisabled(context) ? new AlarmSleepTimer(context)
                : new UptimeSleepTimer();
        networkAccess.renewConfiguration(context);
        return new SignalServiceMessageReceiver(networkAccess.getConfiguration(context),
                new DynamicCredentialsProvider(context),
                BuildConfig.SIGNAL_AGENT,
                new PipeConnectivityListener(),
                sleepTimer,
                provideClientZkOperations().getProfileOperations());
    }

    @Override
    public @NonNull SignalServiceNetworkAccess provideSignalServiceNetworkAccess() {
        return networkAccess;
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
        return new JobManager(context, new JobManager.Configuration.Builder()
                .setDataSerializer(new JsonDataSerializer())
                .setJobFactories(JobManagerFactories.getJobFactories(context))
                .setConstraintFactories(JobManagerFactories.getConstraintFactories(context))
                .setConstraintObservers(JobManagerFactories.getConstraintObservers(context))
                .setJobStorage(new FastJobStorage(DatabaseFactory.getJobDatabase(context)))
                .setJobMigrator(new JobMigrator(TextSecurePreferences.getJobManagerVersion(context), JobManager.CURRENT_VERSION, JobManagerFactories.getJobMigrations(context)))
                .build());
    }

    @Override
    public @NonNull FrameRateTracker provideFrameRateTracker() {
        return new FrameRateTracker(context);
    }

//    @Override
//    public @NonNull KeyValueStore provideKeyValueStore() {
//        return new KeyValueStore(context);
//    }

    @Override
    public @NonNull MegaphoneRepository provideMegaphoneRepository() {
        return new MegaphoneRepository(context);
    }

    @Override
    public @NonNull EarlyMessageCache provideEarlyMessageCache() {
        return new EarlyMessageCache();
    }

    @Override
    public @NonNull InitialMessageRetriever provideInitialMessageRetriever() {
        return new InitialMessageRetriever();
    }

    @Override
    public @NonNull
    MessageNotifier provideMessageNotifier() {
        return new OptimizedMessageNotifier(new DefaultMessageNotifier());
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
        public String getE164() {
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
            TextSecurePreferences.setUnauthorizedReceived(context, false);
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

            // this is to prevent unauthorized clients from sending messages and to trigger the unregistered reminder instantly
            TextSecurePreferences.setPushRegistered(context, false);

            EventBus.getDefault().post(new ReminderUpdateEvent());
        }
    }
}