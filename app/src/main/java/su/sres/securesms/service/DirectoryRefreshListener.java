package su.sres.securesms.service;

import android.content.Context;
import android.content.Intent;

import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobs.DirectorySyncJob;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.securesms.util.TextSecurePreferences;

import java.util.concurrent.TimeUnit;

public class DirectoryRefreshListener extends PersistentAlarmManagerListener {

  private static final long INTERVAL = TimeUnit.HOURS.toMillis(6);

  @Override
  protected long getNextScheduledExecutionTime(Context context) {
    return TextSecurePreferences.getDirectoryRefreshTime(context);
  }

  @Override
  protected long onAlarm(Context context, long scheduledTime) {
    if (scheduledTime != 0 && TextSecurePreferences.isPushRegistered(context) && SignalStore.serviceConfigurationValues().isLicensed()) {
        ApplicationDependencies.getJobManager().add(new DirectorySyncJob(true));
    }

    long newTime = System.currentTimeMillis() + INTERVAL;
    TextSecurePreferences.setDirectoryRefreshTime(context, newTime);

    return newTime;
  }

  public static void schedule(Context context) {
    new DirectoryRefreshListener().onReceive(context, new Intent());
  }
}
