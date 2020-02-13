package su.sres.securesms.mms;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import su.sres.securesms.attachments.Attachment;
import su.sres.securesms.recipients.RecipientId;

import java.util.List;

public class QuoteModel {

  private final long             id;
  private final RecipientId      author;
  private final String           text;
  private final boolean          missing;
  private final List<Attachment> attachments;

  public QuoteModel(long id, @NonNull RecipientId author, String text, boolean missing, @Nullable List<Attachment> attachments) {
    this.id          = id;
    this.author      = author;
    this.text        = text;
    this.missing     = missing;
    this.attachments = attachments;
  }

  public long getId() {
    return id;
  }

  public RecipientId getAuthor() {
    return author;
  }

  public String getText() {
    return text;
  }

  public boolean isOriginalMissing() {
    return missing;
  }

  public List<Attachment> getAttachments() {
    return attachments;
  }
}
