package su.sres.securesms.jobs;

import androidx.annotation.NonNull;

import su.sres.securesms.crypto.UnidentifiedAccessUtil;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.RecipientDatabase;
import su.sres.securesms.database.RecipientDatabase.RecipientReader;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobmanager.impl.NetworkConstraint;
import su.sres.core.util.logging.Log;
import su.sres.securesms.net.NotPushRegisteredException;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientUtil;
import su.sres.securesms.util.TextSecurePreferences;
import su.sres.signalservice.api.SignalServiceMessageSender;
import su.sres.signalservice.api.crypto.UntrustedIdentityException;
import su.sres.signalservice.api.messages.multidevice.BlockedListMessage;
import su.sres.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import su.sres.signalservice.api.push.SignalServiceAddress;
import su.sres.signalservice.api.push.exceptions.PushNetworkException;
import su.sres.signalservice.api.push.exceptions.ServerRejectedException;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MultiDeviceBlockedUpdateJob extends BaseJob {

  public static final String KEY = "MultiDeviceBlockedUpdateJob";

  @SuppressWarnings("unused")
  private static final String TAG = Log.tag(MultiDeviceBlockedUpdateJob.class);

  public MultiDeviceBlockedUpdateJob() {
    this(new Job.Parameters.Builder()
            .addConstraint(NetworkConstraint.KEY)
            .setQueue("MultiDeviceBlockedUpdateJob")
            .setLifespan(TimeUnit.DAYS.toMillis(1))
            .setMaxAttempts(Parameters.UNLIMITED)
            .build());
  }

  private MultiDeviceBlockedUpdateJob(@NonNull Job.Parameters parameters) {
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
  public void onRun()
      throws IOException, UntrustedIdentityException
  {
    if (!Recipient.self().isRegistered()) {
      throw new NotPushRegisteredException();
    }

    if (!TextSecurePreferences.isMultiDevice(context)) {
      Log.i(TAG, "Not multi device, aborting...");
      return;
    }

    RecipientDatabase database = DatabaseFactory.getRecipientDatabase(context);
    try (RecipientReader reader = database.readerForBlocked(database.getBlocked())) {
      List<SignalServiceAddress> blockedIndividuals = new LinkedList<>();
      List<byte[]>               blockedGroups      = new LinkedList<>();

      Recipient recipient;

      while ((recipient = reader.getNext()) != null) {
        if (recipient.isPushGroup()) {
          blockedGroups.add(recipient.requireGroupId().getDecodedId());
        } else if (recipient.hasServiceIdentifier()) {
          blockedIndividuals.add(RecipientUtil.toSignalServiceAddress(context, recipient));
        }
      }

      SignalServiceMessageSender messageSender = ApplicationDependencies.getSignalServiceMessageSender();
      messageSender.sendSyncMessage(SignalServiceSyncMessage.forBlocked(new BlockedListMessage(blockedIndividuals, blockedGroups)),
                                    UnidentifiedAccessUtil.getAccessForSync(context));
    }
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception exception) {
    if (exception instanceof ServerRejectedException) return false;
    if (exception instanceof PushNetworkException) return true;
    return false;
  }

  @Override
  public void onFailure() {
  }

  public static final class Factory implements Job.Factory<MultiDeviceBlockedUpdateJob> {
    @Override
    public @NonNull MultiDeviceBlockedUpdateJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new MultiDeviceBlockedUpdateJob(parameters);
    }
  }
}
