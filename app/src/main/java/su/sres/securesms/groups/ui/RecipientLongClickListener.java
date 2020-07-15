package su.sres.securesms.groups.ui;

import androidx.annotation.NonNull;

import su.sres.securesms.recipients.Recipient;

public interface RecipientLongClickListener {
    boolean onLongClick(@NonNull Recipient recipient);
}