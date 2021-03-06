package su.sres.securesms.messages;

import android.app.Service;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.IBinder;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import su.sres.securesms.jobmanager.ConstraintObserver;
import su.sres.securesms.jobmanager.impl.NetworkConstraint;
import su.sres.securesms.jobmanager.impl.NetworkConstraintObserver;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.securesms.logging.Log;

import su.sres.securesms.ApplicationContext;
import su.sres.securesms.R;
import su.sres.securesms.messages.IncomingMessageProcessor.Processor;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.notifications.NotificationChannels;
import su.sres.securesms.push.SignalServiceNetworkAccess;
import su.sres.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.InvalidVersionException;
import org.whispersystems.libsignal.util.guava.Optional;

import su.sres.signalservice.api.SignalServiceMessagePipe;
import su.sres.signalservice.api.SignalServiceMessageReceiver;
import su.sres.signalservice.api.messages.SignalServiceEnvelope;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class IncomingMessageObserver {

    private static final String TAG = IncomingMessageObserver.class.getSimpleName();

    public  static final  int FOREGROUND_ID            = 313399;
    private static final long REQUEST_TIMEOUT_MINUTES  = 1;

    private static SignalServiceMessagePipe pipe             = null;
    private static SignalServiceMessagePipe unidentifiedPipe = null;

    private final Context                    context;
    private final SignalServiceNetworkAccess networkAccess;
    private final List<Runnable> websocketDrainedListeners;

    private boolean appVisible;

    private volatile boolean websocketDrained;

    public IncomingMessageObserver(@NonNull Context context) {
        this.context                   = context;
        this.networkAccess             = ApplicationDependencies.getSignalServiceNetworkAccess();
        this.websocketDrainedListeners = new CopyOnWriteArrayList<>();

        new MessageRetrievalThread().start();

        if (TextSecurePreferences.isFcmDisabled(context)) {
            ContextCompat.startForegroundService(context, new Intent(context, ForegroundService.class));
        }

        ProcessLifecycleOwner.get().getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onStart(@NonNull LifecycleOwner owner) {
                onAppForegrounded();
            }

            @Override
            public void onStop(@NonNull LifecycleOwner owner) {
                onAppBackgrounded();
            }
        });

        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                synchronized (IncomingMessageObserver.this) {
                    if (!NetworkConstraint.isMet(context)) {
                        Log.w(TAG, "Lost network connection. Shutting down our websocket connections and resetting the drained state.");
                        websocketDrained = false;
                        shutdown(pipe, unidentifiedPipe);
                    }
                    IncomingMessageObserver.this.notifyAll();
                }
            }
        }, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    public synchronized void addWebsocketDrainedListener(@NonNull Runnable listener) {
        websocketDrainedListeners.add(listener);
        if (websocketDrained) {
            listener.run();
        }
    }

    public boolean isWebsocketDrained() {
        return websocketDrained;
    }

    private synchronized void onAppForegrounded() {
        appVisible = true;
        notifyAll();
    }

    private synchronized void onAppBackgrounded() {
        appVisible = false;
        notifyAll();
    }

    private synchronized boolean isConnectionNecessary() {
        boolean registered          = TextSecurePreferences.isPushRegistered(context);
        boolean activated           = SignalStore.serviceConfigurationValues().isLicensed();
        boolean websocketRegistered = TextSecurePreferences.isWebsocketRegistered(context);
        boolean isGcmDisabled       = TextSecurePreferences.isFcmDisabled(context);
        boolean hasNetwork          = NetworkConstraint.isMet(context);

        Log.d(TAG, String.format("Network: %s, Foreground: %s, FCM: %s, Activated: %s, Registered: %s, Websocket Registered: %s",
                hasNetwork, appVisible, !isGcmDisabled, activated, registered, websocketRegistered));

        return registered                    &&
                activated                     &&
                websocketRegistered           &&
                (appVisible || isGcmDisabled) &&
                hasNetwork;
    }

    private synchronized void waitForConnectionNecessary() {
        long timeout = TextSecurePreferences.isPushRegistered(context) ? 0 : 60000;

        try {
            while (!isConnectionNecessary()) wait(timeout);
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
    }

    private void shutdown(@Nullable SignalServiceMessagePipe pipe, @Nullable SignalServiceMessagePipe unidentifiedPipe) {
        try {
            if (pipe != null) {
                pipe.shutdown();
            }
        } catch (Throwable t) {
            Log.w(TAG, "Closing normal pipe failed!", t);
        }

        try {
            if (unidentifiedPipe != null) {
                unidentifiedPipe.shutdown();
            }
        } catch (Throwable t) {
            Log.w(TAG, "Closing unidentified pipe failed!", t);
        }
    }

    public static @Nullable SignalServiceMessagePipe getPipe() {
        return pipe;
    }

    public static @Nullable SignalServiceMessagePipe getUnidentifiedPipe() {
        return unidentifiedPipe;
    }

    private class MessageRetrievalThread extends Thread implements Thread.UncaughtExceptionHandler {

        MessageRetrievalThread() {
            super("MessageRetrievalService");
            setUncaughtExceptionHandler(this);
        }

        @Override
        public void run() {
            while (true) {
                Log.i(TAG, "Waiting for websocket state change....");
                waitForConnectionNecessary();

                Log.i(TAG, "Making websocket connection....");
                SignalServiceMessageReceiver receiver = ApplicationDependencies.getSignalServiceMessageReceiver();

                pipe             = receiver.createMessagePipe();
                unidentifiedPipe = receiver.createUnidentifiedMessagePipe();

                SignalServiceMessagePipe localPipe             = pipe;
                SignalServiceMessagePipe unidentifiedLocalPipe = unidentifiedPipe;

                try {
                    while (isConnectionNecessary()) {
                        try {
                            Log.d(TAG, "Reading message...");
                            Optional<SignalServiceEnvelope> result = localPipe.readOrEmpty(REQUEST_TIMEOUT_MINUTES, TimeUnit.MINUTES, envelope -> {
                                Log.i(TAG, "Retrieved envelope! " + envelope.getTimestamp());
                                try (Processor processor = ApplicationDependencies.getIncomingMessageProcessor().acquire()) {
                                    processor.processEnvelope(envelope);
                                }
                            });

                            if (!result.isPresent() && !websocketDrained) {
                                Log.i(TAG, "Websocket was newly-drained. Triggering listeners.");
                                websocketDrained = true;

                                for (Runnable listener : websocketDrainedListeners) {
                                    listener.run();
                                }
                            }
                        } catch (TimeoutException e) {
                            Log.w(TAG, "Application level read timeout...");
                        } catch (InvalidVersionException e) {
                            Log.w(TAG, e);
                        }
                    }
                } catch (Throwable e) {
                    Log.w(TAG, e);
                } finally {
                    Log.w(TAG, "Shutting down pipe...");
                    shutdown(localPipe, unidentifiedLocalPipe);
                }

                Log.i(TAG, "Looping...");
            }
        }

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            Log.w(TAG, "*** Uncaught exception!");
            Log.w(TAG, e);
        }
    }

    public static class ForegroundService extends Service {

        @Override
        public @Nullable IBinder onBind(Intent intent) {
            return null;
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            super.onStartCommand(intent, flags, startId);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), NotificationChannels.OTHER);
            builder.setContentTitle(getApplicationContext().getString(R.string.MessageRetrievalService_signal));
            builder.setContentText(getApplicationContext().getString(R.string.MessageRetrievalService_background_connection_enabled));
            builder.setPriority(NotificationCompat.PRIORITY_MIN);
            builder.setWhen(0);
            builder.setSmallIcon(R.drawable.ic_signal_background_connection);
            startForeground(FOREGROUND_ID, builder.build());

            return Service.START_STICKY;
        }
    }
}