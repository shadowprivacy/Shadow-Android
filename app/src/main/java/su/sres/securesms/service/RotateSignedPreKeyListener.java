package su.sres.securesms.service;

import android.content.Context;
import android.content.Intent;

import su.sres.securesms.ApplicationContext;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobs.RotateSignedPreKeyJob;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.securesms.util.TextSecurePreferences;

import java.util.concurrent.TimeUnit;

public class RotateSignedPreKeyListener extends PersistentAlarmManagerListener {

  private static final long INTERVAL = TimeUnit.DAYS.toMillis(2);

  @Override
  protected long getNextScheduledExecutionTime(Context context) {
    return TextSecurePreferences.getSignedPreKeyRotationTime(context);
  }

  @Override
  protected long onAlarm(Context context, long scheduledTime) {
    if (scheduledTime != 0 && SignalStore.account().isRegistered()) {
      ApplicationDependencies.getJobManager().add(new RotateSignedPreKeyJob());
    }

    long nextTime = System.currentTimeMillis() + INTERVAL;
    TextSecurePreferences.setSignedPreKeyRotationTime(context, nextTime);

    return nextTime;
  }

  public static void schedule(Context context) {
    new RotateSignedPreKeyListener().onReceive(context, new Intent());
  }
}
