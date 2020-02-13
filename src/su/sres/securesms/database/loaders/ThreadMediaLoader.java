package su.sres.securesms.database.loaders;

import android.content.Context;
import android.database.Cursor;
import androidx.annotation.NonNull;

import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.util.AbstractCursorLoader;

public class ThreadMediaLoader extends AbstractCursorLoader {

  private final RecipientId recipientId;
  private final boolean     gallery;

  public ThreadMediaLoader(@NonNull Context context, @NonNull RecipientId recipientId, boolean gallery) {
    super(context);
    this.recipientId = recipientId;
    this.gallery     = gallery;
  }

  @Override
  public Cursor getCursor() {
    if (recipientId.isUnknown()) return null;

    long threadId = DatabaseFactory.getThreadDatabase(getContext()).getThreadIdFor(Recipient.resolved(recipientId));

    if (gallery) return DatabaseFactory.getMediaDatabase(getContext()).getGalleryMediaForThread(threadId);
    else         return DatabaseFactory.getMediaDatabase(getContext()).getDocumentMediaForThread(threadId);
  }

  public RecipientId getRecipientId() {
    return recipientId;
  }
}
