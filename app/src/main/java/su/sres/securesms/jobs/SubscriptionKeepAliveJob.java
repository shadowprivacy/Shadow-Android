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

  public static void launchSubscriberIdKeepAliveJobIfNecessary() {
    long nextLaunchTime = SignalStore.donationsValues().getLastKeepAliveLaunchTime() + TimeUnit.DAYS.toMillis(3);
    long now            = System.currentTimeMillis();

    if (nextLaunchTime <= now) {
      ApplicationDependencies.getJobManager().add(new SubscriptionKeepAliveJob());
      SignalStore.donationsValues().setLastKeepAliveLaunchTime(now);
    }
  }

  private SubscriptionKeepAliveJob() {
    this(new Parameters.Builder()
             .setQueue(KEY)
             .addConstraint(NetworkConstraint.KEY)
             .setMaxInstancesForQueue(1)
             .setMaxAttempts(Parameters.UNLIMITED)
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

    verifyResponse(response);
    Log.i(TAG, "Successful call to PUT subscription ID");

    ServiceResponse<ActiveSubscription> activeSubscriptionResponse = ApplicationDependencies.getDonationsService()
                                                                                            .getSubscription(subscriber.getSubscriberId())
                                                                                            .blockingGet();

    verifyResponse(activeSubscriptionResponse);
    Log.i(TAG, "Successful call to GET active subscription");

    ActiveSubscription activeSubscription = activeSubscriptionResponse.getResult().get();
    if (activeSubscription.getActiveSubscription() == null || !activeSubscription.getActiveSubscription().isActive()) {
      Log.i(TAG, "User does not have an active subscription. Exiting.");
      return;
    }

    if (activeSubscription.getActiveSubscription().getEndOfCurrentPeriod() > SignalStore.donationsValues().getLastEndOfPeriod()) {
      Log.i(TAG, "Last end of period change. Requesting receipt refresh.");
      SubscriptionReceiptRequestResponseJob.createSubscriptionContinuationJobChain().enqueue();
    }
  }

  private <T> void verifyResponse(@NonNull ServiceResponse<T> response) throws Exception {
    if (response.getExecutionError().isPresent()) {
      Log.w(TAG, "Failed with an execution error. Scheduling retry.", response.getExecutionError().get(), true);
      throw new RetryableException();
    } else if (response.getApplicationError().isPresent()) {
      switch (response.getStatus()) {
        case 403:
        case 404:
          Log.w(TAG, "Invalid or malformed subscriber id. Status: " + response.getStatus(), response.getApplicationError().get(), true);
          throw new IOException();
        default:
          Log.w(TAG, "An unknown server error occurred: " + response.getStatus(), response.getApplicationError().get(), true);
          throw new RetryableException();
      }
    }
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof RetryableException;
  }

  private static class RetryableException extends Exception {
  }

  public static class Factory implements Job.Factory<SubscriptionKeepAliveJob> {
    @Override
    public @NonNull SubscriptionKeepAliveJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new SubscriptionKeepAliveJob(parameters);
    }
  }
}
