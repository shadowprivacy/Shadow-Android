package su.sres.securesms.profiles;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.util.MediaUtil;
import su.sres.signalservice.api.util.StreamDetails;

import java.io.ByteArrayInputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

public class AvatarHelper {

  private static final String AVATAR_DIRECTORY = "avatars";

  public static InputStream getInputStreamFor(@NonNull Context context, @NonNull RecipientId recipientId)
      throws IOException
  {
    return new FileInputStream(getAvatarFile(context, recipientId));
  }

  public static List<File> getAvatarFiles(@NonNull Context context) {
    File   avatarDirectory = new File(context.getFilesDir(), AVATAR_DIRECTORY);
    File[] results         = avatarDirectory.listFiles();

    if (results == null) return new LinkedList<>();
    else                 return Stream.of(results).toList();
  }

  public static void delete(@NonNull Context context, @NonNull RecipientId recipientId) {
    getAvatarFile(context, recipientId).delete();
  }

  public static @NonNull File getAvatarFile(@NonNull Context context, @NonNull RecipientId recipientId) {
    File avatarDirectory = new File(context.getFilesDir(), AVATAR_DIRECTORY);
    avatarDirectory.mkdirs();

    return new File(avatarDirectory, new File(recipientId.serialize()).getName());
  }

  public static void setAvatar(@NonNull Context context, @NonNull RecipientId recipientId, @Nullable byte[] data)
    throws IOException
  {
    if (data == null)  {
      delete(context, recipientId);
    } else {
      FileOutputStream out = new FileOutputStream(getAvatarFile(context, recipientId));
      out.write(data);
      out.close();
    }
  }

  public static @NonNull StreamDetails avatarStream(@NonNull byte[] data) {
    return new StreamDetails(new ByteArrayInputStream(data), MediaUtil.IMAGE_JPEG, data.length);
  }

  public static @Nullable StreamDetails getSelfProfileAvatarStream(@NonNull Context context) {
    File avatarFile = getAvatarFile(context, Recipient.self().getId());

    if (avatarFile.exists() && avatarFile.length() > 0) {
      try {
        FileInputStream stream = new FileInputStream(avatarFile);

        return new StreamDetails(stream, MediaUtil.IMAGE_JPEG, avatarFile.length());
      } catch (FileNotFoundException e) {
        throw new AssertionError(e);
      }
    } else {
      return null;
    }
  }

}
