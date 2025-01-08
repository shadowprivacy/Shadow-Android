package su.sres.securesms.notifications;

import android.content.Context;

import androidx.annotation.NonNull;

import su.sres.securesms.database.RecipientDatabase;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.securesms.recipients.Recipient;

public enum ReplyMethod {

  GroupMessage,
  SecureMessage,
  UnsecuredSmsMessage;

  public static @NonNull ReplyMethod forRecipient(Context context, Recipient recipient) {
    if (recipient.isGroup()) {
      return ReplyMethod.GroupMessage;
    } else if (SignalStore.account().isRegistered() && recipient.getRegistered() == RecipientDatabase.RegisteredState.REGISTERED && !recipient.isForceSmsSelection()) {
      return ReplyMethod.SecureMessage;
    } else {
      return ReplyMethod.UnsecuredSmsMessage;
    }
  }
}