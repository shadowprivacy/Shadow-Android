package su.sres.securesms.dependencies;

import android.app.Application;

import androidx.annotation.NonNull;

import su.sres.securesms.IncomingMessageProcessor;
import su.sres.securesms.gcm.MessageRetriever;
import su.sres.securesms.jobmanager.JobManager;
import su.sres.securesms.keyvalue.KeyValueStore;
import su.sres.securesms.megaphone.MegaphoneRepository;
import su.sres.securesms.push.SignalServiceNetworkAccess;
import su.sres.securesms.recipients.LiveRecipientCache;
import su.sres.securesms.service.IncomingMessageObserver;
import su.sres.securesms.util.FrameRateTracker;
import su.sres.securesms.util.TextSecurePreferences;
import su.sres.signalservice.api.SignalServiceAccountManager;
import su.sres.signalservice.api.SignalServiceMessageReceiver;
import su.sres.signalservice.api.SignalServiceMessageSender;

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

    private static Application application;
    private static Provider    provider;
    private static NetworkIndependentProvider networkIndependentProvider;

    private static SignalServiceAccountManager  accountManager;
    private static SignalServiceMessageSender   messageSender;
    private static SignalServiceMessageReceiver messageReceiver;
    private static IncomingMessageProcessor     incomingMessageProcessor;
    private static MessageRetriever             messageRetriever;
    private static LiveRecipientCache           recipientCache;
    private static JobManager                   jobManager;
    private static FrameRateTracker             frameRateTracker;
    private static KeyValueStore                keyValueStore;
    private static MegaphoneRepository          megaphoneRepository;

    public static synchronized void networkIndependentProviderInit(@NonNull Application application, @NonNull NetworkIndependentProvider networkIndependentProvider) {
        if (ApplicationDependencies.application != null || ApplicationDependencies.networkIndependentProvider != null) {
            throw new IllegalStateException("Already initialized!");
        }

        ApplicationDependencies.application                = application;
        ApplicationDependencies.networkIndependentProvider = networkIndependentProvider;
    }

    public static synchronized void networkDependentProviderInit(@NonNull Provider provider) {
        if (ApplicationDependencies.provider != null) {
            throw new IllegalStateException("Already initialized!");
        }

        ApplicationDependencies.provider    = provider;
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

    public static synchronized @NonNull SignalServiceMessageSender getSignalServiceMessageSender() {
        assertNetworkDependentInitialization();

        if (messageSender == null) {
            messageSender = provider.provideSignalServiceMessageSender();
        } else {
            messageSender.setMessagePipe(IncomingMessageObserver.getPipe(), IncomingMessageObserver.getUnidentifiedPipe());
            messageSender.setIsMultiDevice(TextSecurePreferences.isMultiDevice(application));
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

    public static synchronized @NonNull MessageRetriever getMessageRetriever() {
        assertNetworkDependentInitialization();

        if (messageRetriever == null) {
            messageRetriever = provider.provideMessageRetriever();
        }

        return messageRetriever;
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
        @NonNull SignalServiceAccountManager provideSignalServiceAccountManager();
        @NonNull SignalServiceMessageSender provideSignalServiceMessageSender();
        @NonNull SignalServiceMessageReceiver provideSignalServiceMessageReceiver();
        @NonNull SignalServiceNetworkAccess provideSignalServiceNetworkAccess();
        @NonNull IncomingMessageProcessor provideIncomingMessageProcessor();
        @NonNull MessageRetriever provideMessageRetriever();
        @NonNull LiveRecipientCache provideRecipientCache();
        @NonNull JobManager provideJobManager();
        @NonNull FrameRateTracker provideFrameRateTracker();
// moved to NetworkIndependent       @NonNull KeyValueStore provideKeyValueStore();
        @NonNull MegaphoneRepository provideMegaphoneRepository();
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