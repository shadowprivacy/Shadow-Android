package su.sres.securesms.dependencies;

import android.app.Application;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import su.sres.securesms.components.TypingStatusRepository;
import su.sres.securesms.components.TypingStatusSender;
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

    private static final String TAG = ApplicationDependencies.class.getSimpleName();

    private static Application application;
    private static Provider    provider;
    private static NetworkIndependentProvider networkIndependentProvider;

    private static SignalServiceAccountManager  accountManager;
    private static SignalServiceMessageSender   messageSender;
    private static SignalServiceMessageReceiver messageReceiver;
    private static IncomingMessageObserver      incomingMessageObserver;
    private static IncomingMessageProcessor     incomingMessageProcessor;
    private static BackgroundMessageRetriever   backgroundMessageRetriever;
    private static LiveRecipientCache           recipientCache;
    private static JobManager                   jobManager;
    private static FrameRateTracker             frameRateTracker;
    private static KeyValueStore                keyValueStore;
    private static MegaphoneRepository          megaphoneRepository;
    private static GroupsV2Authorization groupsV2Authorization;
    private static GroupsV2StateProcessor       groupsV2StateProcessor;
    private static GroupsV2Operations           groupsV2Operations;
    private static EarlyMessageCache            earlyMessageCache;
    private static MessageNotifier              messageNotifier;
    private static TrimThreadsByDateManager trimThreadsByDateManager;
    private static TypingStatusRepository typingStatusRepository;
    private static TypingStatusSender typingStatusSender;

    public static synchronized void networkIndependentProviderInit(@NonNull Application application, @NonNull NetworkIndependentProvider networkIndependentProvider) {
        if (ApplicationDependencies.application != null || ApplicationDependencies.networkIndependentProvider != null) {
            throw new IllegalStateException("Already initialized!");
        }

        ApplicationDependencies.application                = application;
        ApplicationDependencies.networkIndependentProvider = networkIndependentProvider;

    }

    @MainThread
    public static synchronized void networkDependentProviderInit(@NonNull Provider provider) {

        if (ApplicationDependencies.provider != null) {
            throw new IllegalStateException("Already initialized!");
        }

        ApplicationDependencies.provider        = provider;
        ApplicationDependencies.messageNotifier = provider.provideMessageNotifier();
        ApplicationDependencies.trimThreadsByDateManager = provider.provideTrimThreadsByDateManager();
    }


    public static @NonNull Application getApplication() {
        assertNetworkIndependentInitialization();
        assertNetworkDependentInitialization();
        return application;
    }

    public static synchronized @NonNull SignalServiceAccountManager getSignalServiceAccountManager() {
        assertNetworkDependentInitialization();

        if (accountManager == null) {
            accountManager = provider.provideSignalServiceAccountManager();
        }

        return accountManager;
    }

    public static synchronized @NonNull GroupsV2Authorization getGroupsV2Authorization() {
        assertNetworkDependentInitialization();

        if (groupsV2Authorization == null) {
            GroupsV2Authorization.ValueCache authCache = new GroupsV2AuthorizationMemoryValueCache(SignalStore.groupsV2AuthorizationCache());
            groupsV2Authorization = new GroupsV2Authorization(getSignalServiceAccountManager().getGroupsV2Api(), authCache);
        }

        return groupsV2Authorization;
    }

    public static synchronized @NonNull GroupsV2Operations getGroupsV2Operations() {
        assertNetworkDependentInitialization();

        if (groupsV2Operations == null) {
            groupsV2Operations = provider.provideGroupsV2Operations();
        }

        return groupsV2Operations;
    }

    public static synchronized @NonNull
    GroupsV2StateProcessor getGroupsV2StateProcessor() {
        assertNetworkDependentInitialization();

        if (groupsV2StateProcessor == null) {
            groupsV2StateProcessor = new GroupsV2StateProcessor(application);
        }

        return groupsV2StateProcessor;
    }

    public static synchronized @NonNull SignalServiceMessageSender getSignalServiceMessageSender() {
        assertNetworkDependentInitialization();

        if (messageSender == null) {
            messageSender = provider.provideSignalServiceMessageSender();
        } else {
            messageSender.update(
                    IncomingMessageObserver.getPipe(),
                    IncomingMessageObserver.getUnidentifiedPipe(),
                    TextSecurePreferences.isMultiDevice(application),
                    FeatureFlags.attachmentsV3());
        }

        return messageSender;
    }

    public static synchronized @NonNull SignalServiceMessageReceiver getSignalServiceMessageReceiver() {
        assertNetworkDependentInitialization();

        if (messageReceiver == null) {
            messageReceiver = provider.provideSignalServiceMessageReceiver();
        }

        return messageReceiver;
    }

    public static synchronized void resetSignalServiceMessageReceiver() {
        assertNetworkDependentInitialization();
        messageReceiver = null;
    }

    public static synchronized @NonNull SignalServiceNetworkAccess getSignalServiceNetworkAccess() {
        assertNetworkDependentInitialization();
        return provider.provideSignalServiceNetworkAccess();
    }

    public static synchronized @NonNull IncomingMessageProcessor getIncomingMessageProcessor() {
        assertNetworkDependentInitialization();

        if (incomingMessageProcessor == null) {
            incomingMessageProcessor = provider.provideIncomingMessageProcessor();
        }

        return incomingMessageProcessor;
    }

    public static synchronized @NonNull BackgroundMessageRetriever getBackgroundMessageRetriever() {
        assertNetworkDependentInitialization();

        if (backgroundMessageRetriever == null) {
            backgroundMessageRetriever = provider.provideBackgroundMessageRetriever();
        }

        return backgroundMessageRetriever;
    }

    public static synchronized @NonNull LiveRecipientCache getRecipientCache() {
        assertNetworkDependentInitialization();

        if (recipientCache == null) {
            recipientCache = provider.provideRecipientCache();
        }

        return recipientCache;
    }

    public static synchronized @NonNull JobManager getJobManager() {
        assertNetworkDependentInitialization();

        if (jobManager == null) {
            jobManager = provider.provideJobManager();
        }

        return jobManager;
    }

    public static synchronized @NonNull FrameRateTracker getFrameRateTracker() {
        assertNetworkDependentInitialization();

        if (frameRateTracker == null) {
            frameRateTracker = provider.provideFrameRateTracker();
        }

        return frameRateTracker;
    }

    public static synchronized @NonNull KeyValueStore getKeyValueStore() {
        assertNetworkIndependentInitialization();

        if (keyValueStore == null) {
            keyValueStore = networkIndependentProvider.provideKeyValueStore();
        }

        return keyValueStore;
    }

    public static synchronized @NonNull MegaphoneRepository getMegaphoneRepository() {
        assertNetworkDependentInitialization();

        if (megaphoneRepository == null) {
            megaphoneRepository = provider.provideMegaphoneRepository();
        }

        return megaphoneRepository;
    }

    public static synchronized @NonNull EarlyMessageCache getEarlyMessageCache() {
        assertNetworkDependentInitialization();

        if (earlyMessageCache == null) {
            earlyMessageCache = provider.provideEarlyMessageCache();
        }

        return earlyMessageCache;
    }

    public static synchronized @NonNull MessageNotifier getMessageNotifier() {
        assertNetworkDependentInitialization();

        return messageNotifier;
    }

    public static synchronized @NonNull IncomingMessageObserver getIncomingMessageObserver() {
        assertNetworkDependentInitialization();
        if (incomingMessageObserver == null) {
            incomingMessageObserver = provider.provideIncomingMessageObserver();
        }

        return incomingMessageObserver;
    }

    public static synchronized @NonNull TrimThreadsByDateManager getTrimThreadsByDateManager() {
        assertNetworkDependentInitialization();
        return trimThreadsByDateManager;
    }

    public static TypingStatusRepository getTypingStatusRepository() {
        assertNetworkDependentInitialization();

        if (typingStatusRepository == null) {
            typingStatusRepository = provider.provideTypingStatusRepository();
        }

        return typingStatusRepository;
    }

    public static TypingStatusSender getTypingStatusSender() {
        assertNetworkDependentInitialization();

        if (typingStatusSender == null) {
            typingStatusSender = provider.provideTypingStatusSender();
        }

        return typingStatusSender;
    }

    private static void assertNetworkDependentInitialization() {
        if (application == null || provider == null) {
            throw new UninitializedException();
        }
    }

    private static void assertNetworkIndependentInitialization() {
        if (application == null || networkIndependentProvider == null) {
            throw new UninitializedException();
        }
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
    }

    public interface NetworkIndependentProvider {
        @NonNull KeyValueStore provideKeyValueStore();
    }

    private static class UninitializedException extends IllegalStateException {
        private UninitializedException() {
            super("You must call init() first!");
        }
    }
}