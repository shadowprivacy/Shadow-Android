package su.sres.securesms.jobs;

import androidx.annotation.NonNull;

import su.sres.core.util.logging.Log;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.util.RecipientAccessList;
import su.sres.signalservice.api.messages.SendMessageResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class GroupSendJobHelper {

  private static final String TAG = Log.tag(GroupSendJobHelper.class);

  private GroupSendJobHelper() {
  }

  static List<Recipient> getCompletedSends(@NonNull List<Recipient> possibleRecipients, @NonNull Collection<SendMessageResult> results) {
    RecipientAccessList accessList  = new RecipientAccessList(possibleRecipients);
    List<Recipient>     completions = new ArrayList<>(results.size());

    for (SendMessageResult sendMessageResult : results) {
      Recipient recipient = accessList.requireByAddress(sendMessageResult.getAddress());

      if (sendMessageResult.getIdentityFailure() != null) {
        Log.w(TAG, "Identity failure for " + recipient.getId());
      }

      if (sendMessageResult.isUnregisteredFailure()) {
        Log.w(TAG, "Unregistered failure for " + recipient.getId());
      }

      if (sendMessageResult.getSuccess() != null ||
          sendMessageResult.getIdentityFailure() != null ||
          sendMessageResult.isUnregisteredFailure())
      {
        completions.add(recipient);
      }
    }

    return completions;
  }
}