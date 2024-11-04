package su.sres.securesms.jobs;

import androidx.annotation.NonNull;

import su.sres.core.util.logging.Log;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobmanager.impl.NetworkConstraint;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.securesms.subscription.Subscriber;
import su.sres.signalservice.api.subscriptions.ActiveSubscription;
import su.sres.signalservice.internal.EmptyResponse;
import su.sres.signalservice.internal.ServiceResponse;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Job that, once there is a valid local subscriber id, should be run every 3 days
 * to ensure that a user's subscription does not lapse.
 */
public class SubscriptionKeepAliveJob extends BaseJob {

  public static final String KEY = "SubscriptionKeepAliveJob";

  private static final String TAG         = Log.tag(SubscriptionKeepAliveJob.class);
  private static final long   JOB_TIMEOUT = TimeUnit.DAYS.toMillis(3);

  public SubscriptionKeepAliveJob() {
    this(new Parameters.Builder()
             .setQueue(KEY)
             .addConstraint(NetworkConstraint.KEY)
             .setMaxInstancesForQueue(1)
             .setLifespan(JOB_TIMEOUT)
             .build());
  }

  private SubscriptionKeepAliveJob(@NonNull Parameters parameters) {
    super(parameters);
  }

  @Override
  public @NonNull Data serialize() {
    return Data.EMPTY;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onFailure() {

  }

  @Override
  protected void onRun() throws Exception {
    Subscriber subscriber = SignalStore.donationsValues().getSubscriber();
    if (subscriber == null) {
      return;
    }

    ServiceResponse<EmptyResponse> response = ApplicationDependencies.getDonationsService()
                                                                     .putSubscription(subscriber.getSubscriberId())
                                                                     .blockingGet();

    if (!response.getResult().isPresent()) {
      if (response.getStatus() == 403) {
        Log.w(TAG, "Response code 403, possibly corrupted subscription id.");
        // TODO [alex] - Probably need some UX around this, or some kind of protocol.
      }

      throw new IOException("Failed to ping subscription service.");
    }

    ServiceResponse<ActiveSubscription> activeSubscriptionResponse = ApplicationDependencies.getDonationsService()
                                                                                            .getSubscription(subscriber.getSubscriberId())
                                                                                            .blockingGet();

    if (!response.getResult().isPresent()) {
      throw new IOException("Failed to perform active subscription check");
    }

    ActiveSubscription activeSubscription = activeSubscriptionResponse.getResult().get();
    if (activeSubscription.getActiveSubscription() == null || !activeSubscription.getActiveSubscription().isActive()) {
      Log.i(TAG, "User does not have an active subscription. Exiting.");
      return;
    }

    if (activeSubscription.getActiveSubscription().getEndOfCurrentPeriod() > SignalStore.donationsValues().getLastEndOfPeriod()) {
      Log.i(TAG, "Last end of period change. Requesting receipt refresh.");
      SubscriptionReceiptRequestResponseJob.enqueueSubscriptionContinuation();
    }
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return true;
  }

  public static class Factory implements Job.Factory<SubscriptionKeepAliveJob> {
    @Override
    public @NonNull SubscriptionKeepAliveJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new SubscriptionKeepAliveJob(parameters);
    }
  }
}
