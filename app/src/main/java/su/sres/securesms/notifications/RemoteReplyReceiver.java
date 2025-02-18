/*
 * Copyright (C) 2016 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package su.sres.securesms.notifications;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.core.app.RemoteInput;

import su.sres.core.util.concurrent.SignalExecutors;
import su.sres.securesms.database.MessageDatabase.MarkedMessageInfo;
import su.sres.securesms.database.ShadowDatabase;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.mms.OutgoingMediaMessage;
import su.sres.securesms.notifications.v2.MessageNotifierV2;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.sms.MessageSender;
import su.sres.securesms.sms.OutgoingEncryptedMessage;
import su.sres.securesms.sms.OutgoingTextMessage;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Get the response text from the Wearable Device and sends an message as a reply
 */
public class RemoteReplyReceiver extends BroadcastReceiver {

  public static final String REPLY_ACTION       = "su.sres.securesms.notifications.WEAR_REPLY";
  public static final String RECIPIENT_EXTRA    = "recipient_extra";
  public static final String REPLY_METHOD       = "reply_method";
  public static final String EARLIEST_TIMESTAMP = "earliest_timestamp";

  @SuppressLint("StaticFieldLeak")
  @Override
  public void onReceive(final Context context, Intent intent) {
    if (!REPLY_ACTION.equals(intent.getAction())) return;

    Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);

    if (remoteInput == null) return;

    final RecipientId  recipientId  = intent.getParcelableExtra(RECIPIENT_EXTRA);
    final ReplyMethod  replyMethod  = (ReplyMethod) intent.getSerializableExtra(REPLY_METHOD);
    final CharSequence responseText = remoteInput.getCharSequence(MessageNotifierV2.EXTRA_REMOTE_REPLY);

    if (recipientId == null) throw new AssertionError("No recipientId specified");
    if (replyMethod == null) throw new AssertionError("No reply method specified");

    if (responseText != null) {
      SignalExecutors.BOUNDED.execute(() -> {
        long threadId;

        Recipient recipient      = Recipient.resolved(recipientId);
        int       subscriptionId = recipient.getDefaultSubscriptionId().or(-1);
        long      expiresIn      = TimeUnit.SECONDS.toMillis(recipient.getExpiresInSeconds());

        switch (replyMethod) {
          case GroupMessage: {
            OutgoingMediaMessage reply = new OutgoingMediaMessage(recipient,
                                                                  responseText.toString(),
                                                                  new LinkedList<>(),
                                                                  System.currentTimeMillis(),
                                                                  subscriptionId,
                                                                  expiresIn,
                                                                  false,
                                                                  0,
                                                                  null,
                                                                  Collections.emptyList(),
                                                                  Collections.emptyList(),
                                                                  Collections.emptyList(),
                                                                  Collections.emptySet(),
                                                                  Collections.emptySet());
            threadId = MessageSender.send(context, reply, -1, false, null, null);
            break;
          }
          case SecureMessage: {
            OutgoingEncryptedMessage reply = new OutgoingEncryptedMessage(recipient, responseText.toString(), expiresIn);
            threadId = MessageSender.send(context, reply, -1, false, null, null);
            break;
          }
          case UnsecuredSmsMessage: {
            OutgoingTextMessage reply = new OutgoingTextMessage(recipient, responseText.toString(), expiresIn, subscriptionId);
            threadId = MessageSender.send(context, reply, -1, true, null, null);
            break;
          }
          default:
            throw new AssertionError("Unknown Reply method");
        }

        ApplicationDependencies.getMessageNotifier().addStickyThread(threadId, intent.getLongExtra(EARLIEST_TIMESTAMP, System.currentTimeMillis()));

        List<MarkedMessageInfo> messageIds = ShadowDatabase.threads().setRead(threadId, true);

        ApplicationDependencies.getMessageNotifier().updateNotification(context);
        MarkReadReceiver.process(context, messageIds);
      });
    }
  }
}
