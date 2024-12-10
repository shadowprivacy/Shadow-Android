package su.sres.securesms.components.settings.app.subscription.manage

import io.reactivex.rxjava3.core.Observable
import su.sres.securesms.dependencies.ApplicationDependencies
import su.sres.securesms.jobmanager.JobTracker
import su.sres.securesms.jobs.DonationReceiptRedemptionJob
import su.sres.securesms.jobs.SubscriptionReceiptRequestResponseJob
import su.sres.securesms.keyvalue.SignalStore
import org.whispersystems.libsignal.util.guava.Optional
import java.util.concurrent.TimeUnit

/**
 * Allows observer to poll for the status of the latest pending, running, or completed redemption job for subscriptions.
 */
object SubscriptionRedemptionJobWatcher {
  fun watch(): Observable<Optional<JobTracker.JobState>> = Observable.interval(0, 5, TimeUnit.SECONDS).map {
    val redemptionJobState: JobTracker.JobState? = ApplicationDependencies.getJobManager().getFirstMatchingJobState {
      it.factoryKey == DonationReceiptRedemptionJob.KEY && it.parameters.queue == DonationReceiptRedemptionJob.SUBSCRIPTION_QUEUE
    }

    val receiptJobState: JobTracker.JobState? = ApplicationDependencies.getJobManager().getFirstMatchingJobState {
      it.factoryKey == SubscriptionReceiptRequestResponseJob.KEY && it.parameters.queue == DonationReceiptRedemptionJob.SUBSCRIPTION_QUEUE
    }

    val jobState: JobTracker.JobState? = redemptionJobState ?: receiptJobState

    if (jobState == null && SignalStore.donationsValues().getSubscriptionRedemptionFailed()) {
      Optional.of(JobTracker.JobState.FAILURE)
    } else {
      Optional.fromNullable(jobState)
    }
  }.distinctUntilChanged()
}