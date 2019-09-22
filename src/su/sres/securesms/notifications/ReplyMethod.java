package su.sres.securesms.notifications;

import android.content.Context;
import androidx.annotation.NonNull;

import su.sres.securesms.database.RecipientDatabase;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.util.TextSecurePreferences;

public enum ReplyMethod {

    GroupMessage,
    SecureMessage,
    UnsecuredSmsMessage;

    public static @NonNull ReplyMethod forRecipient(Context context, Recipient recipient) {
        if (recipient.isGroupRecipient()) {
            return ReplyMethod.GroupMessage;
        } else if (TextSecurePreferences.isPushRegistered(context) && recipient.getRegistered() == RecipientDatabase.RegisteredState.REGISTERED && !recipient.isForceSmsSelection()) {
            return ReplyMethod.SecureMessage;
        } else {
            return ReplyMethod.UnsecuredSmsMessage;
        }
    }
}