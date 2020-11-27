package su.sres.securesms.mms;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import su.sres.securesms.attachments.Attachment;
import su.sres.securesms.database.model.Mention;
import su.sres.securesms.recipients.RecipientId;

import java.util.Collections;
import java.util.List;

public class QuoteModel {

  private final long             id;
  private final RecipientId      author;
  private final String           text;
  private final boolean          missing;
  private final List<Attachment> attachments;
  private final List<Mention>    mentions;

  public QuoteModel(long id, @NonNull RecipientId author, String text, boolean missing, @Nullable List<Attachment> attachments, @Nullable List<Mention> mentions) {
    this.id          = id;
    this.author      = author;
    this.text        = text;
    this.missing     = missing;
    this.attachments = attachments;
    this.mentions    = mentions != null ? mentions : Collections.emptyList();
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

  public @NonNull List<Mention> getMentions() {
    return mentions;
  }
}
