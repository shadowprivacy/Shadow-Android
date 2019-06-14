package su.sres.securesms.jobs;

import android.support.annotation.NonNull;

import su.sres.securesms.ApplicationContext;
import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobmanager.impl.NetworkConstraint;
import su.sres.securesms.logging.Log;

import su.sres.securesms.dependencies.InjectableType;

import su.sres.securesms.crypto.UnidentifiedAccessUtil;
import su.sres.securesms.util.TextSecurePreferences;
import su.sres.signalservice.api.SignalServiceAccountManager;
import su.sres.signalservice.api.push.exceptions.NetworkFailureException;

import java.io.IOException;

import javax.inject.Inject;

public class RefreshAttributesJob extends BaseJob implements InjectableType {

  public static final String KEY = "RefreshAttributesJob";

  private static final String TAG = RefreshAttributesJob.class.getSimpleName();

  @Inject SignalServiceAccountManager signalAccountManager;

  public RefreshAttributesJob() {
    this(new Job.Parameters.Builder()
            .addConstraint(NetworkConstraint.KEY)
            .setQueue("RefreshAttributesJob")
            .build());
  }

  private RefreshAttributesJob(@NonNull Job.Parameters parameters) {
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
  public void onRun() throws IOException {
    int     registrationId              = TextSecurePreferences.getLocalRegistrationId(context);
    boolean fetchesMessages             = TextSecurePreferences.isFcmDisabled(context);
    String  pin                         = TextSecurePreferences.getRegistrationLockPin(context);
    byte[]  unidentifiedAccessKey       = UnidentifiedAccessUtil.getSelfUnidentifiedAccessKey(context);
    boolean universalUnidentifiedAccess = TextSecurePreferences.isUniversalUnidentifiedAccess(context);

    signalAccountManager.setAccountAttributes(null, registrationId, fetchesMessages, pin,
            unidentifiedAccessKey, universalUnidentifiedAccess);


    ApplicationContext.getInstance(context)
            .getJobManager()
            .add(new RefreshUnidentifiedDeliveryAbilityJob());
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof NetworkFailureException;
  }

  @Override
  public void onCanceled() {
    Log.w(TAG, "Failed to update account attributes!");
  }

  public static class Factory implements Job.Factory<RefreshAttributesJob> {
    @Override
    public @NonNull RefreshAttributesJob create(@NonNull Parameters parameters, @NonNull su.sres.securesms.jobmanager.Data data) {
      return new RefreshAttributesJob(parameters);
    }
  }
}
