package su.sres.securesms.jobs;

import androidx.annotation.NonNull;

import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobmanager.impl.NetworkConstraint;
import su.sres.securesms.logging.Log;

import su.sres.securesms.dependencies.ApplicationDependencies;

import su.sres.securesms.AppCapabilities;
import su.sres.securesms.crypto.ProfileKeyUtil;
import su.sres.securesms.util.TextSecurePreferences;
import su.sres.signalservice.api.SignalServiceAccountManager;
import su.sres.signalservice.api.crypto.UnidentifiedAccess;
import su.sres.signalservice.api.profiles.SignalServiceProfile;
import su.sres.signalservice.api.push.exceptions.NetworkFailureException;

import java.io.IOException;

public class RefreshAttributesJob extends BaseJob  {

  public static final String KEY = "RefreshAttributesJob";

  private static final String TAG = RefreshAttributesJob.class.getSimpleName();

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
    if (!TextSecurePreferences.isPushRegistered(context) || TextSecurePreferences.getLocalNumber(context) == null) {
      Log.w(TAG, "Not yet registered. Skipping.");
      return;
    }

    int     registrationId              = TextSecurePreferences.getLocalRegistrationId(context);
    boolean fetchesMessages             = TextSecurePreferences.isFcmDisabled(context);
    String  pin                         = TextSecurePreferences.getRegistrationLockPin(context);
    byte[]    unidentifiedAccessKey       = UnidentifiedAccess.deriveAccessKeyFrom(ProfileKeyUtil.getSelfProfileKey());
    boolean universalUnidentifiedAccess = TextSecurePreferences.isUniversalUnidentifiedAccess(context);

    // dummy true instead of checking whether the client has a PIN
    SignalServiceProfile.Capabilities capabilities = AppCapabilities.getCapabilities(true);

    Log.i(TAG, "Capabilities:" +
            "\n    Storage? " + capabilities.isStorage() +
            "\n    GV2? " + capabilities.isGv2() +
            "\n    UUID? " + capabilities.isUuid())  ;

    SignalServiceAccountManager signalAccountManager = ApplicationDependencies.getSignalServiceAccountManager();
    signalAccountManager.setAccountAttributes(null, registrationId, fetchesMessages, pin,
            unidentifiedAccessKey, universalUnidentifiedAccess,
            capabilities);

  }

  @Override
  public boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof NetworkFailureException;
  }

  @Override
  public void onFailure() {
    Log.w(TAG, "Failed to update account attributes!");
  }

  public static class Factory implements Job.Factory<RefreshAttributesJob> {
    @Override
    public @NonNull RefreshAttributesJob create(@NonNull Parameters parameters, @NonNull su.sres.securesms.jobmanager.Data data) {
      return new RefreshAttributesJob(parameters);
    }
  }
}
