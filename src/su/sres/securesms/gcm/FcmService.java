package su.sres.securesms.gcm;

import android.content.Context;
import android.os.Build;
import android.os.PowerManager;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import su.sres.securesms.ApplicationContext;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobmanager.impl.NetworkConstraint;
import su.sres.securesms.jobs.FcmRefreshJob;
import su.sres.securesms.jobs.PushNotificationReceiveJob;
import su.sres.securesms.logging.Log;
import su.sres.securesms.registration.PushChallengeRequest;
import su.sres.securesms.util.PowerManagerCompat;
import su.sres.securesms.util.ServiceUtil;
import su.sres.securesms.util.TextSecurePreferences;
import su.sres.securesms.util.WakeLockUtil;
import su.sres.signalservice.api.SignalServiceMessageReceiver;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class FcmService extends FirebaseMessagingService {

  private static final String TAG = FcmService.class.getSimpleName();

  private static final String   WAKE_LOCK_TAG  = "FcmMessageProcessing";
  private static final long     SOCKET_TIMEOUT = TimeUnit.SECONDS.toMillis(10);

  private static int activeCount;

  @Override
  public void onMessageReceived(RemoteMessage remoteMessage) {
    Log.i(TAG, "FCM message... Delay: " + (System.currentTimeMillis() - remoteMessage.getSentTime()));
    String challenge = remoteMessage.getData().get("challenge");
    if (challenge != null) {
      handlePushChallenge(challenge);
    } else {

      WakeLockUtil.runWithLock(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK, 60000, WAKE_LOCK_TAG, () ->
              handleReceivedNotification(getApplicationContext())
      );
    }
  }

  @Override
  public void onNewToken(String token) {
    Log.i(TAG, "onNewToken()");

    if (!TextSecurePreferences.isPushRegistered(getApplicationContext())) {
      Log.i(TAG, "Got a new FCM token, but the user isn't registered.");
      return;
    }

    ApplicationContext.getInstance(getApplicationContext())
            .getJobManager()
            .add(new FcmRefreshJob());
  }

  private void handleReceivedNotification(Context context) {
    if (!incrementActiveGcmCount()) {
      Log.i(TAG, "Skipping FCM processing -- there's already one enqueued.");
      return;
    }

    TextSecurePreferences.setNeedsMessagePull(context, true);

    long                         startTime       = System.currentTimeMillis();
    SignalServiceMessageReceiver messageReceiver = ApplicationDependencies.getSignalServiceMessageReceiver();
    PowerManager                 powerManager    = ServiceUtil.getPowerManager(getApplicationContext());
    boolean                      doze            = PowerManagerCompat.isDeviceIdleMode(powerManager);
    boolean                      network         = new NetworkConstraint.Factory(ApplicationContext.getInstance(context)).create().isMet();

    if (doze || !network) {
      Log.w(TAG, "We may be operating in a constrained environment. Doze: " + doze + " Network: " + network);
    }

    try {
      messageReceiver.setSoTimeoutMillis(SOCKET_TIMEOUT);
      new PushNotificationReceiveJob(context).pullAndProcessMessages(messageReceiver, TAG, startTime);
    } catch (IOException e) {
      if (Build.VERSION.SDK_INT >= 26) {
        Log.i(TAG, "Failed to retrieve the envelope. Scheduling on the system JobScheduler (API " + Build.VERSION.SDK_INT + ").", e);
        FcmJobService.schedule(context);
      } else {
        Log.i(TAG, "Failed to retrieve the envelope. Scheduling on JobManager (API " + Build.VERSION.SDK_INT + ").", e);
        ApplicationContext.getInstance(context)
                .getJobManager()
                .add(new PushNotificationReceiveJob(context));
      }
    }

    decrementActiveGcmCount();
    Log.i(TAG, "Processing complete.");
  }

  private static void handlePushChallenge(@NonNull String challenge) {
    Log.d(TAG, String.format("Got a push challenge \"%s\"", challenge));

    PushChallengeRequest.postChallengeResponse(challenge);
  }

  private static synchronized boolean incrementActiveGcmCount() {
    if (activeCount < 2) {
      activeCount++;
      return true;
    }
    return false;
  }

  private static synchronized void decrementActiveGcmCount() {
    activeCount--;
  }
}