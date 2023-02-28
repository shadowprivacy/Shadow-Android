package su.sres.securesms.dependencies;

import android.app.Application;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import su.sres.securesms.components.TypingStatusRepository;
import su.sres.securesms.components.TypingStatusSender;
import su.sres.securesms.database.DatabaseObserver;
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
import su.sres.securesms.notifications.MessageNotifier;
import su.sres.securesms.push.SignalServiceNetworkAccess;
import su.sres.securesms.recipients.LiveRecipientCache;
import su.sres.securesms.service.TrimThreadsByDateManager;
import su.sres.securesms.util.EarlyMessageCache;
import su.sres.securesms.util.FeatureFlags;
import su.sres.securesms.util.FrameRateTracker;
import su.sres.securesms.util.TextSecurePreferences;
import su.sres.signalservice.api.SignalServiceAccountManager;
import su.sres.signalservice.api.SignalServiceMessageReceiver;
import su.sres.signalservice.api.SignalServiceMessageSender;
import su.sres.signalservice.api.groupsv2.GroupsV2Operations;

/**
 * Location for storing and retrieving application-scoped singletons. Users must call
 * {@link #networkIndependentProviderInit(Application, NetworkIndependentProvider)} and
 * {@link #networkDependentProviderInit(Provider)} before using any of the methods, preferably early on in
 * {@link Application#onCreate()}.
 *
 * All future application-scoped singletons should be written as normal objects, then placed here
 * to manage their singleton-ness.
 */
public class ApplicationDependencies {

    private static final Object LOCK                    = new Object();
    private static final Object NI_LOCK                    = new Object();
    private static final Object FRAME_RATE_TRACKER_LOCK = new Object();

    private static Application application;
    private static Provider    provider;
    private static NetworkIndependentProvider networkIndependentProvider;

    private static MessageNotifier          messageNotifier;
    private static TrimThreadsByDateManager trimThreadsByDateManager;

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
    private static volatile DatabaseObserver databaseObserver;
    private static volatile KeyValueStore keyValueStore;

    public static void networkIndependentProviderInit(@NonNull Application application, @NonNull NetworkIndependentProvider networkIndependentProvider) {
        synchronized (NI_LOCK) {
            if (ApplicationDependencies.application != null || ApplicationDependencies.networkIndependentProvider != null) {
                throw new IllegalStateException("Already initialized!");
            }
        }

        ApplicationDependencies.application                = application;
        ApplicationDependencies.networkIndependentProvider = networkIndependentProvider;

    }

    @MainThread
    public static void networkDependentProviderInit(@NonNull Provider provider) {

        synchronized (LOCK) {
            if (ApplicationDependencies.provider != null) {
                throw new IllegalStateException("Already initialized!");
            }
        }

        ApplicationDependencies.provider        = provider;
        ApplicationDependencies.messageNotifier = provider.provideMessageNotifier();
        ApplicationDependencies.trimThreadsByDateManager = provider.provideTrimThreadsByDateManager();
    }


    public static @NonNull Application getApplication() {
        return application;
    }

    public static @NonNull SignalServiceAccountManager getSignalServiceAccountManager() {
        synchronized (LOCK) {
            if (accountManager == null) {
                accountManager = provider.provideSignalServiceAccountManager();
            }
        }

        return accountManager;
    }

    public static @NonNull GroupsV2Authorization getGroupsV2Authorization() {

        synchronized (LOCK) {
            if (groupsV2Authorization == null) {
                GroupsV2Authorization.ValueCache authCache = new GroupsV2AuthorizationMemoryValueCache(SignalStore.groupsV2AuthorizationCache());
                groupsV2Authorization = new GroupsV2Authorization(getSignalServiceAccountManager().getGroupsV2Api(), authCache);
            }
        }

        return groupsV2Authorization;
    }

    public static @NonNull GroupsV2Operations getGroupsV2Operations() {

        synchronized (LOCK) {
            if (groupsV2Operations == null) {
                groupsV2Operations = provider.provideGroupsV2Operations();
            }
        }

        return groupsV2Operations;
    }

    public static @NonNull GroupsV2StateProcessor getGroupsV2StateProcessor() {

        synchronized (LOCK) {
            if (groupsV2StateProcessor == null) {
                groupsV2StateProcessor = new GroupsV2StateProcessor(application);
            }
        }

        return groupsV2StateProcessor;
    }

    public static @NonNull SignalServiceMessageSender getSignalServiceMessageSender() {

        synchronized (LOCK) {
            if (messageSender == null) {
                messageSender = provider.provideSignalServiceMessageSender();
            } else {
                messageSender.update(
                        IncomingMessageObserver.getPipe(),
                        IncomingMessageObserver.getUnidentifiedPipe(),
                        TextSecurePreferences.isMultiDevice(application),
                        FeatureFlags.attachmentsV3());
            }
        }

        return messageSender;
    }

    public static @NonNull SignalServiceMessageReceiver getSignalServiceMessageReceiver() {

        synchronized (LOCK) {
            if (messageReceiver == null) {
                messageReceiver = provider.provideSignalServiceMessageReceiver();
            }
        }

        return messageReceiver;
    }

    public static void resetSignalServiceMessageReceiver() {
        synchronized (LOCK) {
            messageReceiver = null;
        }
    }

    public static @NonNull SignalServiceNetworkAccess getSignalServiceNetworkAccess() {
        return provider.provideSignalServiceNetworkAccess();
    }

    public static @NonNull IncomingMessageProcessor getIncomingMessageProcessor() {
        synchronized (LOCK) {
            if (incomingMessageProcessor == null) {
                incomingMessageProcessor = provider.provideIncomingMessageProcessor();
            }
        }

        return incomingMessageProcessor;
    }

    public static @NonNull BackgroundMessageRetriever getBackgroundMessageRetriever() {

        synchronized (LOCK) {
            if (backgroundMessageRetriever == null) {
                backgroundMessageRetriever = provider.provideBackgroundMessageRetriever();
            }
        }

        return backgroundMessageRetriever;
    }

    public static @NonNull LiveRecipientCache getRecipientCache() {

        synchronized (LOCK) {
            if (recipientCache == null) {
                recipientCache = provider.provideRecipientCache();
            }
        }

        return recipientCache;
    }

    public static @NonNull JobManager getJobManager() {

        synchronized (LOCK) {
            if (jobManager == null) {
                jobManager = provider.provideJobManager();
            }
        }

        return jobManager;
    }

    public static @NonNull FrameRateTracker getFrameRateTracker() {

        synchronized (FRAME_RATE_TRACKER_LOCK) {
            if (frameRateTracker == null) {
                frameRateTracker = provider.provideFrameRateTracker();
            }
        }

        return frameRateTracker;
    }

    public static @NonNull KeyValueStore getKeyValueStore() {

        synchronized (NI_LOCK) {
            if (keyValueStore == null) {
                keyValueStore = networkIndependentProvider.provideKeyValueStore();
            }
        }

        return keyValueStore;
    }

    public static @NonNull MegaphoneRepository getMegaphoneRepository() {

        synchronized (LOCK) {
            if (megaphoneRepository == null) {
                megaphoneRepository = provider.provideMegaphoneRepository();
            }
        }

        return megaphoneRepository;
    }

    public static @NonNull EarlyMessageCache getEarlyMessageCache() {

        synchronized (LOCK) {
            if (earlyMessageCache == null) {
                earlyMessageCache = provider.provideEarlyMessageCache();
            }
        }

        return earlyMessageCache;
    }

    public static @NonNull MessageNotifier getMessageNotifier() {
        return messageNotifier;
    }

    public static @NonNull IncomingMessageObserver getIncomingMessageObserver() {
        synchronized (LOCK) {
            if (incomingMessageObserver == null) {
                incomingMessageObserver = provider.provideIncomingMessageObserver();
            }
        }

        return incomingMessageObserver;
    }

    public static @NonNull TrimThreadsByDateManager getTrimThreadsByDateManager() {
        return trimThreadsByDateManager;
    }

    public static TypingStatusRepository getTypingStatusRepository() {

        if (typingStatusRepository == null) {
            typingStatusRepository = provider.provideTypingStatusRepository();
        }

        return typingStatusRepository;
    }

    public static TypingStatusSender getTypingStatusSender() {

        if (typingStatusSender == null) {
            typingStatusSender = provider.provideTypingStatusSender();
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

    public interface Provider {
        @NonNull
        GroupsV2Operations provideGroupsV2Operations();
        @NonNull SignalServiceAccountManager provideSignalServiceAccountManager();
        @NonNull SignalServiceMessageSender provideSignalServiceMessageSender();
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
        @NonNull TypingStatusRepository provideTypingStatusRepository();
        @NonNull TypingStatusSender provideTypingStatusSender();
        @NonNull DatabaseObserver provideDatabaseObserver();
    }

    public interface NetworkIndependentProvider {
        @NonNull KeyValueStore provideKeyValueStore();

    }
}