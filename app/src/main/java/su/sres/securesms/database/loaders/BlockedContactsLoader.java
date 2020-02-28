package su.sres.securesms.database.loaders;

import android.content.Context;
import android.database.Cursor;

import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.util.AbstractCursorLoader;

public class BlockedContactsLoader extends AbstractCursorLoader {

  public BlockedContactsLoader(Context context) {
    super(context);
  }

  @Override
  public Cursor getCursor() {
    return DatabaseFactory.getRecipientDatabase(getContext()).getBlocked();
  }
}
