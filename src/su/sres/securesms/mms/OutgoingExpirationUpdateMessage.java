package su.sres.securesms.mms;

import su.sres.securesms.attachments.Attachment;
import su.sres.securesms.database.ThreadDatabase;
import su.sres.securesms.recipients.Recipient;

import java.util.Collections;
import java.util.LinkedList;

public class OutgoingExpirationUpdateMessage extends OutgoingSecureMediaMessage {

  public OutgoingExpirationUpdateMessage(Recipient recipient, long sentTimeMillis, long expiresIn) {
    super(recipient, "", new LinkedList<Attachment>(), sentTimeMillis,
            ThreadDatabase.DistributionTypes.CONVERSATION, expiresIn, null, Collections.emptyList(),
            Collections.emptyList());
  }

  @Override
  public boolean isExpirationUpdate() {
    return true;
  }

}
