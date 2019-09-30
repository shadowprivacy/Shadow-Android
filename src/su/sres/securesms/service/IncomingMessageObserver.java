package su.sres.securesms.service;

import android.app.Service;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import su.sres.securesms.jobmanager.ConstraintObserver;
import su.sres.securesms.jobmanager.impl.NetworkConstraint;
import su.sres.securesms.jobmanager.impl.NetworkConstraintObserver;
import su.sres.securesms.logging.Log;

import su.sres.securesms.ApplicationContext;
import su.sres.securesms.R;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobs.PushContentReceiveJob;
import su.sres.securesms.notifications.NotificationChannels;
import su.sres.securesms.push.SignalServiceNetworkAccess;
import su.sres.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.InvalidVersionException;
import su.sres.signalservice.api.SignalServiceMessagePipe;
import su.sres.signalservice.api.SignalServiceMessageReceiver;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;



public class IncomingMessageObserver implements ConstraintObserver.Notifier {

    private static final String TAG = IncomingMessageObserver.class.getSimpleName();

    public  static final  int FOREGROUND_ID            = 313399;
    private static final long REQUEST_TIMEOUT_MINUTES  = 1;

    private static SignalServiceMessagePipe pipe             = null;
    private static SignalServiceMessagePipe unidentifiedPipe = null;

    private final Context                      context;
    private final NetworkConstraint            networkConstraint;
    private final SignalServiceMessageReceiver receiver;
    private final SignalServiceNetworkAccess   networkAccess;

    private boolean appVisible;

    public IncomingMessageObserver(@NonNull Context context) {
        this.context           = context;
        this.networkConstraint = new NetworkConstraint.Factory(ApplicationContext.getInstance(context)).create();

        new NetworkConstraintObserver(ApplicationContext.getInstance(context)).register(this);
        this.receiver          = ApplicationDependencies.getSignalServiceMessageReceiver();
        this.networkAccess     = ApplicationDependencies.getSignalServiceNetworkAccess();
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
    }

    @Override
    public void onConstraintMet(@NonNull String reason) {
        synchronized (this) {
            notifyAll();
        }
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
        boolean isGcmDisabled = TextSecurePreferences.isFcmDisabled(context);

        Log.d(TAG, String.format("Network requirement: %s, app visible: %s, gcm disabled: %b",
                networkConstraint.isMet(), appVisible, isGcmDisabled));

        return TextSecurePreferences.isPushRegistered(context)      &&
                TextSecurePreferences.isWebsocketRegistered(context) &&
                (appVisible || isGcmDisabled)                        &&
                networkConstraint.isMet()                       &&
                !networkAccess.isCensored(context);
    }

    private synchronized void waitForConnectionNecessary() {
        try {
            while (!isConnectionNecessary()) wait();
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
    }

    private void shutdown(SignalServiceMessagePipe pipe, SignalServiceMessagePipe unidentifiedPipe) {
        try {
            pipe.shutdown();
            unidentifiedPipe.shutdown();
        } catch (Throwable t) {
            Log.w(TAG, t);
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
                pipe             = receiver.createMessagePipe();
                unidentifiedPipe = receiver.createUnidentifiedMessagePipe();

                SignalServiceMessagePipe localPipe             = pipe;
                SignalServiceMessagePipe unidentifiedLocalPipe = unidentifiedPipe;

                try {
                    while (isConnectionNecessary()) {
                        try {
                            Log.i(TAG, "Reading message...");
                            localPipe.read(REQUEST_TIMEOUT_MINUTES, TimeUnit.MINUTES,
                                    envelope -> {
                                        Log.i(TAG, "Retrieved envelope! " + String.valueOf(envelope.getSource()));
                                        new PushContentReceiveJob(context).processEnvelope(envelope);
                                    });
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