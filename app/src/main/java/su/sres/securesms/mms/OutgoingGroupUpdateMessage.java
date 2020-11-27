package su.sres.securesms.mms;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import su.sres.securesms.attachments.Attachment;
import su.sres.securesms.contactshare.Contact;
import su.sres.securesms.database.ThreadDatabase;
import su.sres.securesms.database.model.Mention;
import su.sres.securesms.database.model.databaseprotos.DecryptedGroupV2Context;
import su.sres.securesms.linkpreview.LinkPreview;
import su.sres.securesms.recipients.Recipient;
import su.sres.signalservice.internal.push.SignalServiceProtos.GroupContext;

import java.util.Collections;
import java.util.List;

public final class OutgoingGroupUpdateMessage extends OutgoingSecureMediaMessage {

  private final MessageGroupContext messageGroupContext;

  public OutgoingGroupUpdateMessage(@NonNull Recipient recipient,
                                   @NonNull MessageGroupContext groupContext,
                                   @NonNull List<Attachment> avatar,
                                   long sentTimeMillis,
                                   long expiresIn,
                                   boolean viewOnce,
                                   @Nullable QuoteModel quote,
                                   @NonNull List<Contact> contacts,
                                    @NonNull List<LinkPreview> previews,
                                    @NonNull List<Mention> mentions)
  {
    super(recipient, groupContext.getEncodedGroupContext(), avatar, sentTimeMillis,
            ThreadDatabase.DistributionTypes.CONVERSATION, expiresIn, viewOnce, quote, contacts, previews, mentions);

    this.messageGroupContext = groupContext;
  }

  public OutgoingGroupUpdateMessage(@NonNull Recipient recipient,
                                   @NonNull GroupContext group,
                                   @Nullable final Attachment avatar,
                                   long sentTimeMillis,
                                   long expireIn,
                                   boolean viewOnce,
                                   @Nullable QuoteModel quote,
                                   @NonNull List<Contact> contacts,
                                    @NonNull List<LinkPreview> previews,
                                    @NonNull List<Mention> mentions)
  {
    this(recipient, new MessageGroupContext(group), getAttachments(avatar), sentTimeMillis, expireIn, viewOnce, quote, contacts, previews, mentions);
  }

  public OutgoingGroupUpdateMessage(@NonNull Recipient recipient,
                                   @NonNull DecryptedGroupV2Context group,
                                   @Nullable final Attachment avatar,
                                   long sentTimeMillis,
                                   long expireIn,
                                   boolean viewOnce,
                                   @Nullable QuoteModel quote,
                                   @NonNull List<Contact> contacts,
                                    @NonNull List<LinkPreview> previews,
                                    @NonNull List<Mention> mentions)
  {
    this(recipient, new MessageGroupContext(group), getAttachments(avatar), sentTimeMillis, expireIn, viewOnce, quote, contacts, previews, mentions);
  }

  @Override
  public boolean isGroup() {
    return true;
  }

  public boolean isV2Group() {
    return messageGroupContext.isV2Group();
  }

  public @NonNull MessageGroupContext.GroupV1Properties requireGroupV1Properties() {
    return messageGroupContext.requireGroupV1Properties();
  }

  public @NonNull MessageGroupContext.GroupV2Properties requireGroupV2Properties() {
    return messageGroupContext.requireGroupV2Properties();
  }

  private static List<Attachment> getAttachments(@Nullable Attachment avatar) {
    return avatar == null ? Collections.emptyList() : Collections.singletonList(avatar);
  }
}
