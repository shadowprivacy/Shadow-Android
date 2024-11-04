package su.sres.securesms.jobs;

import androidx.annotation.NonNull;

import su.sres.core.util.logging.Log;
import org.signal.zkgroup.receipts.ReceiptCredentialPresentation;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobmanager.impl.NetworkConstraint;
import su.sres.signalservice.internal.EmptyResponse;
import su.sres.signalservice.internal.ServiceResponse;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Job to redeem a verified donation receipt. It is up to the Job prior in the chain to specify a valid
 * presentation object via setOutputData. This is expected to be the byte[] blob of a ReceiptCredentialPresentation object.
 */
public class DonationReceiptRedemptionJob extends BaseJob {
  private static final String TAG = Log.tag(DonationReceiptRedemptionJob.class);

  public static final String KEY                                   = "DonationReceiptRedemptionJob";
  public static final String INPUT_RECEIPT_CREDENTIAL_PRESENTATION = "data.receipt.credential.presentation";

  public static DonationReceiptRedemptionJob createJob() {
    return new DonationReceiptRedemptionJob(
        new Job.Parameters
            .Builder()
            .addConstraint(NetworkConstraint.KEY)
            .setQueue("ReceiptRedemption")
            .setMaxAttempts(Parameters.UNLIMITED)
            .setMaxInstancesForQueue(1)
            .setLifespan(TimeUnit.DAYS.toMillis(7))
            .build());
  }

  private DonationReceiptRedemptionJob(@NonNull Job.Parameters parameters) {
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
    Data inputData = getInputData();

    if (inputData == null) {
      Log.w(TAG, "No input data. Failing.");
      throw new IllegalStateException("Expected a presentation object in input data.");
    }

    byte[] presentationBytes = inputData.getStringAsBlob(INPUT_RECEIPT_CREDENTIAL_PRESENTATION);
    if (presentationBytes == null) {
      Log.d(TAG, "No response data. Exiting.");
      return;
    }

    ReceiptCredentialPresentation presentation = new ReceiptCredentialPresentation(presentationBytes);

    ServiceResponse<EmptyResponse> response = ApplicationDependencies.getDonationsService()
                                                                     .redeemReceipt(presentation, false, false)
                                                                     .blockingGet();

    if (response.getApplicationError().isPresent()) {
      Log.w(TAG, "Encountered a non-recoverable exception", response.getApplicationError().get());
      throw new IOException(response.getApplicationError().get());
    } else if (response.getExecutionError().isPresent()) {
      Log.w(TAG, "Encountered a retryable exception", response.getExecutionError().get());
      throw new RetryableException();
    }
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof RetryableException;
  }

  private final static class RetryableException extends Exception {
  }

  public static class Factory implements Job.Factory<DonationReceiptRedemptionJob> {
    @Override
    public @NonNull DonationReceiptRedemptionJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new DonationReceiptRedemptionJob(parameters);
    }
  }
}
