package su.sres.securesms.jobs;

import android.graphics.Bitmap;
import androidx.annotation.NonNull;

import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.GroupDatabase;
import su.sres.securesms.database.GroupDatabase.GroupRecord;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.groups.GroupId;
import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobmanager.impl.NetworkConstraint;
import su.sres.securesms.logging.Log;
import su.sres.securesms.mms.AttachmentStreamUriLoader.AttachmentModel;
import su.sres.securesms.profiles.AvatarHelper;
import su.sres.securesms.util.BitmapDecodingException;
import su.sres.securesms.util.BitmapUtil;
import su.sres.securesms.util.Hex;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.util.guava.Optional;
import su.sres.signalservice.api.SignalServiceMessageReceiver;
import su.sres.signalservice.api.messages.SignalServiceAttachmentPointer;
import su.sres.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class AvatarDownloadJob extends BaseJob {

  public static final String KEY = "AvatarDownloadJob";

  private static final String TAG = AvatarDownloadJob.class.getSimpleName();

  private static final int MAX_AVATAR_SIZE = 20 * 1024 * 1024;

  private static final String KEY_GROUP_ID = "group_id";

  private @NonNull GroupId.V1 groupId;

  public AvatarDownloadJob(@NonNull GroupId.V1 groupId) {
    this(new Job.Parameters.Builder()
                    .addConstraint(NetworkConstraint.KEY)
                    .setMaxAttempts(10)
                    .build(),
            groupId);
  }

  private AvatarDownloadJob(@NonNull Job.Parameters parameters, @NonNull GroupId.V1 groupId) {
    super(parameters);

    this.groupId = groupId;
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder().putString(KEY_GROUP_ID, groupId.toString()).build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws IOException {
    GroupDatabase         database   = DatabaseFactory.getGroupDatabase(context);
    Optional<GroupRecord> record     = database.getGroup(groupId);
    File                  attachment = null;

    try {
      if (record.isPresent()) {
        long             avatarId    = record.get().getAvatarId();
        String           contentType = record.get().getAvatarContentType();
        byte[]           key         = record.get().getAvatarKey();
        String           relay       = record.get().getRelay();
        Optional<byte[]> digest      = Optional.fromNullable(record.get().getAvatarDigest());
        Optional<String> fileName    = Optional.absent();

        if (avatarId == -1 || key == null) {
          return;
        }

        if (digest.isPresent()) {
          Log.i(TAG, "Downloading group avatar with digest: " + Hex.toString(digest.get()));
        }

        attachment = File.createTempFile("avatar", "tmp", context.getCacheDir());
        attachment.deleteOnExit();

        SignalServiceMessageReceiver   receiver    = ApplicationDependencies.getSignalServiceMessageReceiver();
        SignalServiceAttachmentPointer pointer     = new SignalServiceAttachmentPointer(avatarId, contentType, key, Optional.of(0), Optional.absent(), 0, 0, digest, fileName, false, Optional.absent(), Optional.absent());
        InputStream                    inputStream = receiver.retrieveAttachment(pointer, attachment, AvatarHelper.AVATAR_DOWNLOAD_FAILSAFE_MAX_SIZE);

        AvatarHelper.setAvatar(context, record.get().getRecipientId(), inputStream);
        DatabaseFactory.getGroupDatabase(context).onAvatarUpdated(groupId, true);

        inputStream.close();
      }
    } catch (NonSuccessfulResponseCodeException | InvalidMessageException e) {
      Log.w(TAG, e);
    } finally {
      if (attachment != null)
        attachment.delete();
    }
  }

  @Override
  public void onFailure() { }

  @Override
  public boolean onShouldRetry(@NonNull Exception exception) {
    if (exception instanceof IOException) return true;
    return false;
  }

  public static final class Factory implements Job.Factory<AvatarDownloadJob> {
    @Override
    public @NonNull AvatarDownloadJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new AvatarDownloadJob(parameters, GroupId.parse(data.getString(KEY_GROUP_ID)).requireV1());
    }
  }
}
