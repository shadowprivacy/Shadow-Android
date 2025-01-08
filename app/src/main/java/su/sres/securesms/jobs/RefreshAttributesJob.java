package su.sres.securesms.jobs;

import androidx.annotation.NonNull;

import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobmanager.impl.NetworkConstraint;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.core.util.logging.Log;

import su.sres.securesms.dependencies.ApplicationDependencies;

import su.sres.securesms.AppCapabilities;
import su.sres.securesms.crypto.ProfileKeyUtil;
import su.sres.securesms.util.TextSecurePreferences;
import su.sres.signalservice.api.SignalServiceAccountManager;
import su.sres.signalservice.api.crypto.UnidentifiedAccess;
import su.sres.signalservice.api.push.exceptions.NetworkFailureException;
import su.sres.signalservice.api.account.AccountAttributes;

import java.io.IOException;

public class RefreshAttributesJob extends BaseJob {

  public static final String KEY = "RefreshAttributesJob";

  private static final String TAG = Log.tag(RefreshAttributesJob.class);

  private static final String KEY_FORCED = "forced";

  private static volatile boolean hasRefreshedThisAppCycle;

  private final boolean forced;

  public RefreshAttributesJob() {
    this(true);
  }

  /**
   * @param forced True if you want this job to run no matter what. False if you only want this job
   *               to run if it hasn't run yet this app cycle.
   */
  public RefreshAttributesJob(boolean forced) {
    this(new Job.Parameters.Builder()
             .addConstraint(NetworkConstraint.KEY)
             .setQueue("RefreshAttributesJob")
             .setMaxInstancesForFactory(2)
             .build(),
         forced);
  }

  private RefreshAttributesJob(@NonNull Job.Parameters parameters, boolean forced) {
    super(parameters);
    this.forced = forced;
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder().putBoolean(KEY_FORCED, forced).build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws IOException {
    if (!SignalStore.account().isRegistered() || SignalStore.account().getUserLogin() == null) {
      Log.w(TAG, "Not yet registered. Skipping.");
      return;
    }

    if (!forced && hasRefreshedThisAppCycle) {
      Log.d(TAG, "Already refreshed this app cycle. Skipping.");
      return;
    }

    int     registrationId              = SignalStore.account().getRegistrationId();
    boolean fetchesMessages             = !SignalStore.account().isFcmEnabled();
    String  pin                         = TextSecurePreferences.getRegistrationLockPin(context);
    byte[]  unidentifiedAccessKey       = UnidentifiedAccess.deriveAccessKeyFrom(ProfileKeyUtil.getSelfProfileKey());
    boolean universalUnidentifiedAccess = TextSecurePreferences.isUniversalUnidentifiedAccess(context);

    boolean userLoginDiscoverable = SignalStore.userLoginPrivacy().getUserLoginListingMode().isDiscoverable();

    // dummy true instead of checking whether the client has a PIN
    AccountAttributes.Capabilities capabilities = AppCapabilities.getCapabilities(true);

    Log.i(TAG, "User login discoverable : " + userLoginDiscoverable +
               "\n  Capabilities:" +
               "\n    Storage? " + capabilities.isStorage() +
               "\n    GV2? " + capabilities.isGv2() +
               "\n    GV1 Migration? " + capabilities.isGv1Migration() +
               "\n    Sender Key? " + capabilities.isSenderKey() +
               "\n    Announcement Groups? " + capabilities.isAnnouncementGroup() +
               "\n    Change Login? " + capabilities.isChangeLogin() +
               "\n    UUID? " + capabilities.isUuid());

    SignalServiceAccountManager signalAccountManager = ApplicationDependencies.getSignalServiceAccountManager();
    signalAccountManager.setAccountAttributes(null, registrationId, fetchesMessages, pin,
                                              unidentifiedAccessKey, universalUnidentifiedAccess,
                                              capabilities,
                                              userLoginDiscoverable);

    ApplicationDependencies.getJobManager().add(new RefreshOwnProfileJob());

    hasRefreshedThisAppCycle = true;
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
      return new RefreshAttributesJob(parameters, data.getBooleanOrDefault(KEY_FORCED, true));
    }
  }
}
