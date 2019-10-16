package su.sres.securesms.recipients;

import androidx.annotation.NonNull;

public interface RecipientModifiedListener {
  public void onModified(@NonNull Recipient recipient);
}
