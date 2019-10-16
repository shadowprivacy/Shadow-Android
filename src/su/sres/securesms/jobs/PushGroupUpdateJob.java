package su.sres.securesms.jobs;

import androidx.annotation.NonNull;

import su.sres.securesms.crypto.UnidentifiedAccessUtil;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.GroupDatabase;
import su.sres.securesms.database.GroupDatabase.GroupRecord;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobmanager.impl.NetworkConstraint;
import su.sres.securesms.logging.Log;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.util.GroupUtil;
import org.whispersystems.libsignal.util.guava.Optional;
import su.sres.signalservice.api.SignalServiceMessageSender;
import su.sres.signalservice.api.crypto.UntrustedIdentityException;
import su.sres.signalservice.api.messages.SignalServiceAttachment;
import su.sres.signalservice.api.messages.SignalServiceAttachmentStream;
import su.sres.signalservice.api.messages.SignalServiceDataMessage;
import su.sres.signalservice.api.messages.SignalServiceGroup;
import su.sres.signalservice.api.messages.SignalServiceGroup.Type;
import su.sres.signalservice.api.push.SignalServiceAddress;
import su.sres.signalservice.api.push.exceptions.PushNetworkException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;



public class PushGroupUpdateJob extends BaseJob  {

  public static final String KEY = "PushGroupUpdateJob";

  private static final String TAG = PushGroupUpdateJob.class.getSimpleName();

  private static final String KEY_SOURCE   = "source";
  private static final String KEY_GROUP_ID = "group_id";

  private RecipientId source;
  private byte[]      groupId;

  public PushGroupUpdateJob(@NonNull RecipientId source, byte[] groupId) {
    this(new Job.Parameters.Builder()
                    .addConstraint(NetworkConstraint.KEY)
                    .setLifespan(TimeUnit.DAYS.toMillis(1))
                    .setMaxAttempts(Parameters.UNLIMITED)
                    .build(),
            source,
            groupId);
  }

  private PushGroupUpdateJob(@NonNull Job.Parameters parameters, RecipientId source, byte[] groupId) {
    super(parameters);

    this.source  = source;
    this.groupId = groupId;
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder().putString(KEY_SOURCE, source.serialize())
            .putString(KEY_GROUP_ID, GroupUtil.getEncodedId(groupId, false))
            .build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws IOException, UntrustedIdentityException {
    GroupDatabase           groupDatabase = DatabaseFactory.getGroupDatabase(context);
    Optional<GroupRecord>   record        = groupDatabase.getGroup(GroupUtil.getEncodedId(groupId, false));
    SignalServiceAttachment avatar        = null;

    if (record == null) {
      Log.w(TAG, "No information for group record info request: " + new String(groupId));
      return;
    }

    if (record.get().getAvatar() != null) {
      avatar = SignalServiceAttachmentStream.newStreamBuilder()
                                            .withContentType("image/jpeg")
                                            .withStream(new ByteArrayInputStream(record.get().getAvatar()))
                                            .withLength(record.get().getAvatar().length)
                                            .build();
    }

    List<String> members = new LinkedList<>();

    for (RecipientId member : record.get().getMembers()) {
      members.add(Recipient.resolved(member).requireAddress().serialize());
    }

    SignalServiceGroup groupContext = SignalServiceGroup.newBuilder(Type.UPDATE)
                                                        .withAvatar(avatar)
                                                        .withId(groupId)
                                                        .withMembers(members)
                                                        .withName(record.get().getTitle())
                                                        .build();

    RecipientId groupRecipientId = DatabaseFactory.getRecipientDatabase(context).getOrInsertFromGroupId(GroupUtil.getEncodedId(groupId, false));
    Recipient   groupRecipient   = Recipient.resolved(groupRecipientId);

    SignalServiceDataMessage message = SignalServiceDataMessage.newBuilder()
                                                               .asGroupMessage(groupContext)
                                                               .withTimestamp(System.currentTimeMillis())
                                                               .withExpiration(groupRecipient.getExpireMessages())
                                                               .build();

    SignalServiceMessageSender messageSender = ApplicationDependencies.getSignalServiceMessageSender();
    Recipient                  recipient     = Recipient.resolved(source);

    messageSender.sendMessage(new SignalServiceAddress(recipient.requireAddress().serialize()),
            UnidentifiedAccessUtil.getAccessFor(context, recipient),
            message);
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception e) {
    Log.w(TAG, e);
    return e instanceof PushNetworkException;
  }

  @Override
  public void onCanceled() {
  }

  public static final class Factory implements Job.Factory<PushGroupUpdateJob> {
    @Override
    public @NonNull PushGroupUpdateJob create(@NonNull Parameters parameters, @NonNull su.sres.securesms.jobmanager.Data data) {
      try {
        return new PushGroupUpdateJob(parameters,
                RecipientId.from(data.getString(KEY_SOURCE)),
                GroupUtil.getDecodedId(data.getString(KEY_GROUP_ID)));
      } catch (IOException e) {
        throw new AssertionError(e);
      }
    }
  }
}
