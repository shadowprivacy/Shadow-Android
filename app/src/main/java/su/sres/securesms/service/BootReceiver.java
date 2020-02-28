package su.sres.securesms.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import su.sres.securesms.ApplicationContext;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobs.PushNotificationReceiveJob;

public class BootReceiver extends BroadcastReceiver {

  @Override
  public void onReceive(Context context, Intent intent) {
    ApplicationDependencies.getJobManager().add(new PushNotificationReceiveJob(context));
  }
}
