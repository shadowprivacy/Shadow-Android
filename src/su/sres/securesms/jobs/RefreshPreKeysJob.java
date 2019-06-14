package su.sres.securesms.jobs;

import android.support.annotation.NonNull;

import su.sres.securesms.ApplicationContext;
import su.sres.securesms.crypto.IdentityKeyUtil;
import su.sres.securesms.crypto.PreKeyUtil;
import su.sres.securesms.dependencies.InjectableType;
import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobmanager.impl.NetworkConstraint;
import su.sres.securesms.logging.Log;
import su.sres.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import su.sres.signalservice.api.SignalServiceAccountManager;
import su.sres.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import su.sres.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;
import java.util.List;

import javax.inject.Inject;

public class RefreshPreKeysJob extends BaseJob implements InjectableType {

  public static final String KEY = "RefreshPreKeysJob";

  private static final String TAG = RefreshPreKeysJob.class.getSimpleName();

  private static final int PREKEY_MINIMUM = 10;

  @Inject SignalServiceAccountManager accountManager;

  public RefreshPreKeysJob() {
    this(new Job.Parameters.Builder()
            .setQueue("RefreshPreKeysJob")
            .addConstraint(NetworkConstraint.KEY)
            .setMaxAttempts(5)
            .build());
  }

  private RefreshPreKeysJob(@NonNull Job.Parameters parameters) {
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
    if (!TextSecurePreferences.isPushRegistered(context)) return;

    int availableKeys = accountManager.getPreKeysCount();

    if (availableKeys >= PREKEY_MINIMUM && TextSecurePreferences.isSignedPreKeyRegistered(context)) {
      Log.i(TAG, "Available keys sufficient: " + availableKeys);
      return;
    }

    List<PreKeyRecord> preKeyRecords       = PreKeyUtil.generatePreKeys(context);
    IdentityKeyPair    identityKey         = IdentityKeyUtil.getIdentityKeyPair(context);
    SignedPreKeyRecord signedPreKeyRecord  = PreKeyUtil.generateSignedPreKey(context, identityKey, false);

    Log.i(TAG, "Registering new prekeys...");

    accountManager.setPreKeys(identityKey.getPublicKey(), signedPreKeyRecord, preKeyRecords);

    PreKeyUtil.setActiveSignedPreKeyId(context, signedPreKeyRecord.getId());
    TextSecurePreferences.setSignedPreKeyRegistered(context, true);

    ApplicationContext.getInstance(context)
                      .getJobManager()
            .add(new CleanPreKeysJob());
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception exception) {
    if (exception instanceof NonSuccessfulResponseCodeException) return false;
    if (exception instanceof PushNetworkException)               return true;

    return false;
  }

  @Override
  public void onCanceled() {
  }

  public static final class Factory implements Job.Factory<RefreshPreKeysJob> {
    @Override
    public @NonNull RefreshPreKeysJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new RefreshPreKeysJob(parameters);
    }
  }
}
