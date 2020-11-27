package su.sres.securesms.database.model;

import android.text.SpannableString;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import su.sres.securesms.components.mention.MentionAnnotation;
import su.sres.securesms.mms.SlideDeck;
import su.sres.securesms.recipients.RecipientId;

public class Quote {

  private final long          id;
  private final RecipientId   author;
  private final CharSequence  text;
  private final boolean       missing;
  private final SlideDeck     attachment;
  private final List<Mention> mentions;

  public Quote(long id,
               @NonNull RecipientId author,
               @Nullable CharSequence text,
               boolean missing,
               @NonNull SlideDeck attachment,
               @NonNull List<Mention> mentions)
  {
    this.id                = id;
    this.author            = author;
    this.missing           = missing;
    this.attachment        = attachment;
    this.mentions          = mentions;

    SpannableString spannable = new SpannableString(text);
    MentionAnnotation.setMentionAnnotations(spannable, mentions);

    this.text = spannable;
  }

  public long getId() {
    return id;
  }

  public @NonNull RecipientId getAuthor() {
    return author;
  }

  public @Nullable CharSequence getDisplayText() {
    return text;
  }

  public boolean isOriginalMissing() {
    return missing;
  }

  public @NonNull SlideDeck getAttachment() {
    return attachment;
  }
}
