package su.sres.securesms.util;


import android.content.Context;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import android.text.TextUtils;

import su.sres.securesms.database.NoSuchMessageException;
import su.sres.securesms.logging.Log;

import su.sres.securesms.attachments.AttachmentId;
import su.sres.securesms.attachments.DatabaseAttachment;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.model.MessageRecord;
import su.sres.securesms.recipients.Recipient;

import java.util.Collections;
import java.util.Set;

public class AttachmentUtil {

  private static final String TAG = AttachmentUtil.class.getSimpleName();

  @WorkerThread
  public static boolean isAutoDownloadPermitted(@NonNull Context context, @Nullable DatabaseAttachment attachment) {
    if (attachment == null) {
      Log.w(TAG, "attachment was null, returning vacuous true");
      return true;
    }

    if (!isFromTrustedConversation(context, attachment)) {
      return false;
    }

    Set<String> allowedTypes = getAllowedAutoDownloadTypes(context);
    String      contentType  = attachment.getContentType();

    if (attachment.isVoiceNote()                                                       ||
            (MediaUtil.isAudio(attachment) && TextUtils.isEmpty(attachment.getFileName())) ||
            MediaUtil.isLongTextType(attachment.getContentType())                          ||
            attachment.isSticker())
    {
      return true;
    } else if (isNonDocumentType(contentType)) {
      return allowedTypes.contains(MediaUtil.getDiscreteMimeType(contentType));
    } else {
      return allowedTypes.contains("documents");
    }
  }

  /**
   * Deletes the specified attachment. If its the only attachment for its linked message, the entire
   * message is deleted.
   */
  @WorkerThread
  public static void deleteAttachment(@NonNull Context context,
                                      @NonNull DatabaseAttachment attachment)
  {
    AttachmentId attachmentId    = attachment.getAttachmentId();
    long         mmsId           = attachment.getMmsId();
    int          attachmentCount = DatabaseFactory.getAttachmentDatabase(context)
        .getAttachmentsForMessage(mmsId)
        .size();

    if (attachmentCount <= 1) {
      DatabaseFactory.getMmsDatabase(context).delete(mmsId);
    } else {
      DatabaseFactory.getAttachmentDatabase(context).deleteAttachment(attachmentId);
    }
  }

  private static boolean isNonDocumentType(String contentType) {
    return
        MediaUtil.isImageType(contentType) ||
        MediaUtil.isVideoType(contentType) ||
        MediaUtil.isAudioType(contentType);
  }

  private static @NonNull Set<String> getAllowedAutoDownloadTypes(@NonNull Context context) {
    if      (isConnectedWifi(context))    return TextSecurePreferences.getWifiMediaDownloadAllowed(context);
    else if (isConnectedRoaming(context)) return TextSecurePreferences.getRoamingMediaDownloadAllowed(context);
    else if (isConnectedMobile(context))  return TextSecurePreferences.getMobileMediaDownloadAllowed(context);
    else                                  return Collections.emptySet();
  }

  private static NetworkInfo getNetworkInfo(@NonNull Context context) {
    return ServiceUtil.getConnectivityManager(context).getActiveNetworkInfo();
  }

  private static boolean isConnectedWifi(@NonNull Context context) {
    final NetworkInfo info = getNetworkInfo(context);
    return info != null && info.isConnected() && info.getType() == ConnectivityManager.TYPE_WIFI;
  }

  private static boolean isConnectedMobile(@NonNull Context context) {
    final NetworkInfo info = getNetworkInfo(context);
    return info != null && info.isConnected() && info.getType() == ConnectivityManager.TYPE_MOBILE;
  }

  private static boolean isConnectedRoaming(@NonNull Context context) {
    final NetworkInfo info = getNetworkInfo(context);
    return info != null && info.isConnected() && info.isRoaming() && info.getType() == ConnectivityManager.TYPE_MOBILE;
  }

  @WorkerThread
  private static boolean isFromTrustedConversation(@NonNull Context context, @NonNull DatabaseAttachment attachment) {
    try {
      MessageRecord message = DatabaseFactory.getMmsDatabase(context).getMessageRecord(attachment.getMmsId());

      Recipient individualRecipient = message.getRecipient();
      Recipient threadRecipient = DatabaseFactory.getThreadDatabase(context).getRecipientForThreadId(message.getThreadId());

      if (threadRecipient != null && threadRecipient.isGroup()) {
        return threadRecipient.isProfileSharing() || isTrustedIndividual(individualRecipient, message);
      } else {
        return isTrustedIndividual(individualRecipient, message);
      }
    } catch (NoSuchMessageException e) {
      Log.w(TAG, "Message could not be found! Assuming not a trusted contact.");
      return false;
    }
  }

  private static boolean isTrustedIndividual(@NonNull Recipient recipient, @NonNull MessageRecord message) {
    return recipient.isSystemContact()  ||
            recipient.isProfileSharing() ||
            message.isOutgoing()         ||
            recipient.isLocalNumber();
  }
}
