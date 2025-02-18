package su.sres.securesms.database.loaders;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import su.sres.core.util.ThreadUtil;
import su.sres.core.util.logging.Log;
import su.sres.securesms.attachments.AttachmentId;
import su.sres.securesms.database.AttachmentDatabase;
import su.sres.securesms.database.DatabaseObserver;
import su.sres.securesms.database.MediaDatabase;
import su.sres.securesms.database.MediaDatabase.Sorting;
import su.sres.securesms.database.ShadowDatabase;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.mms.PartAuthority;
import su.sres.securesms.util.AsyncLoader;

public final class PagingMediaLoader extends AsyncLoader<Pair<Cursor, Integer>> {

  @SuppressWarnings("unused")
  private static final String TAG = Log.tag(PagingMediaLoader.class);

  private final Uri                       uri;
  private final boolean                   leftIsRecent;
  private final Sorting                   sorting;
  private final long                      threadId;
  private final DatabaseObserver.Observer observer;

  public PagingMediaLoader(@NonNull Context context, long threadId, @NonNull Uri uri, boolean leftIsRecent, @NonNull Sorting sorting) {
    super(context);
    this.threadId     = threadId;
    this.uri          = uri;
    this.leftIsRecent = leftIsRecent;
    this.sorting      = sorting;
    this.observer     = () -> {
      ThreadUtil.runOnMain(this::onContentChanged);
    };
  }

  @Override
  public @Nullable Pair<Cursor, Integer> loadInBackground() {
    ApplicationDependencies.getDatabaseObserver().registerAttachmentObserver(observer);
    Cursor cursor = ShadowDatabase.media().getGalleryMediaForThread(threadId, sorting, threadId == MediaDatabase.ALL_THREADS);

    while (cursor.moveToNext()) {
      AttachmentId attachmentId  = new AttachmentId(cursor.getLong(cursor.getColumnIndexOrThrow(AttachmentDatabase.ROW_ID)), cursor.getLong(cursor.getColumnIndexOrThrow(AttachmentDatabase.UNIQUE_ID)));
      Uri          attachmentUri = PartAuthority.getAttachmentDataUri(attachmentId);

      if (attachmentUri.equals(uri)) {
        return new Pair<>(cursor, leftIsRecent ? cursor.getPosition() : cursor.getCount() - 1 - cursor.getPosition());
      }
    }

    return null;
  }

  @Override
  protected void onAbandon() {
    ApplicationDependencies.getDatabaseObserver().unregisterObserver(observer);
  }
}
