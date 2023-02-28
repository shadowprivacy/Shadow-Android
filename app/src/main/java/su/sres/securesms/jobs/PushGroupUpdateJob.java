package su.sres.securesms.jobs;

import androidx.annotation.NonNull;

import su.sres.securesms.crypto.UnidentifiedAccessUtil;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.GroupDatabase;
import su.sres.securesms.database.GroupDatabase.GroupRecord;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.groups.GroupId;
import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobmanager.impl.NetworkConstraint;
import su.sres.core.util.logging.Log;
import su.sres.securesms.profiles.AvatarHelper;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.recipients.RecipientUtil;
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

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class PushGroupUpdateJob extends BaseJob  {

  public static final String KEY = "PushGroupUpdateJob";

  private static final String TAG = PushGroupUpdateJob.class.getSimpleName();

  private static final String KEY_SOURCE   = "source";
  private static final String KEY_GROUP_ID = "group_id";

  private final RecipientId source;
  private final GroupId     groupId;

  public PushGroupUpdateJob(@NonNull RecipientId source, @NonNull GroupId groupId) {
    this(new Job.Parameters.Builder()
                    .addConstraint(NetworkConstraint.KEY)
                    .setLifespan(TimeUnit.DAYS.toMillis(1))
                    .setMaxAttempts(Parameters.UNLIMITED)
                    .build(),
            source,
            groupId);
  }

  private PushGroupUpdateJob(@NonNull Job.Parameters parameters, RecipientId source, @NonNull GroupId groupId) {
    super(parameters);

    this.source  = source;
    this.groupId = groupId;
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder().putString(KEY_SOURCE, source.serialize())
            .putString(KEY_GROUP_ID, groupId.toString())
            .build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws IOException, UntrustedIdentityException {
    GroupDatabase           groupDatabase = DatabaseFactory.getGroupDatabase(context);
    Optional<GroupRecord>   record        = groupDatabase.getGroup(groupId);
    SignalServiceAttachment avatar        = null;

    if (record == null || !record.isPresent()) {
      Log.w(TAG, "No information for group record info request: " + groupId.toString());
      return;
    }

    if (AvatarHelper.hasAvatar(context, record.get().getRecipientId())) {
      avatar = SignalServiceAttachmentStream.newStreamBuilder()
                                            .withContentType("image/jpeg")
              .withStream(AvatarHelper.getAvatar(context, record.get().getRecipientId()))
              .withLength(AvatarHelper.getAvatarLength(context, record.get().getRecipientId()))
                                            .build();
    }

    List<SignalServiceAddress> members = new LinkedList<>();

    for (RecipientId member : record.get().getMembers()) {
      Recipient recipient = Recipient.resolved(member);
      members.add(RecipientUtil.toSignalServiceAddress(context, recipient));
    }

    SignalServiceGroup groupContext = SignalServiceGroup.newBuilder(Type.UPDATE)
                                                        .withAvatar(avatar)
            .withId(groupId.getDecodedId())
                                                        .withMembers(members)
                                                        .withName(record.get().getTitle())
                                                        .build();

    RecipientId groupRecipientId = DatabaseFactory.getRecipientDatabase(context).getOrInsertFromGroupId(groupId);
    Recipient   groupRecipient   = Recipient.resolved(groupRecipientId);

    SignalServiceDataMessage message = SignalServiceDataMessage.newBuilder()
                                                               .asGroupMessage(groupContext)
                                                               .withTimestamp(System.currentTimeMillis())
                                                               .withExpiration(groupRecipient.getExpireMessages())
                                                               .build();

    SignalServiceMessageSender messageSender = ApplicationDependencies.getSignalServiceMessageSender();
    Recipient                  recipient     = Recipient.resolved(source);

    messageSender.sendMessage(RecipientUtil.toSignalServiceAddress(context, recipient),
            UnidentifiedAccessUtil.getAccessFor(context, recipient),
            message);
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception e) {
    Log.w(TAG, e);
    return e instanceof PushNetworkException;
  }

  @Override
  public void onFailure() {
  }

  public static final class Factory implements Job.Factory<PushGroupUpdateJob> {
    @Override
    public @NonNull PushGroupUpdateJob create(@NonNull Parameters parameters, @NonNull su.sres.securesms.jobmanager.Data data) {
      return new PushGroupUpdateJob(parameters,
              RecipientId.from(data.getString(KEY_SOURCE)),
              GroupId.parseOrThrow(data.getString(KEY_GROUP_ID)));
    }
  }
}
