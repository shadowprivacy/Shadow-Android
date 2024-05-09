package su.sres.securesms.components.identity;

import android.content.Context;
import android.content.DialogInterface;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import su.sres.securesms.R;
import su.sres.securesms.crypto.ReentrantSessionLock;
import su.sres.securesms.crypto.storage.TextSecureIdentityKeyStore;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.IdentityDatabase;
import su.sres.securesms.database.model.IdentityRecord;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.util.concurrent.SimpleTask;
import su.sres.signalservice.api.SignalSessionLock;

import java.util.List;

public class UntrustedSendDialog extends AlertDialog.Builder implements DialogInterface.OnClickListener {

  private final List<IdentityRecord> untrustedRecords;
  private final ResendListener       resendListener;

  public UntrustedSendDialog(@NonNull Context context,
                             @NonNull String message,
                             @NonNull List<IdentityRecord> untrustedRecords,
                             @NonNull ResendListener resendListener)
  {
    super(context);
    this.untrustedRecords = untrustedRecords;
    this.resendListener   = resendListener;

    setTitle(R.string.UntrustedSendDialog_send_message);
    setIcon(R.drawable.ic_warning);
    setMessage(message);
    setPositiveButton(R.string.UntrustedSendDialog_send, this);
    setNegativeButton(android.R.string.cancel, null);
  }

  @Override
  public void onClick(DialogInterface dialog, int which) {
    final TextSecureIdentityKeyStore identityStore = ApplicationDependencies.getIdentityStore();

    SimpleTask.run(() -> {
      try(SignalSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
        for (IdentityRecord identityRecord : untrustedRecords) {
          identityStore.setApproval(identityRecord.getRecipientId(), true);
        }
      }

      return null;
    }, unused -> resendListener.onResendMessage());
  }

  public interface ResendListener {
    public void onResendMessage();
  }
}
