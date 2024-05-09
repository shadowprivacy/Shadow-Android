package su.sres.securesms.components.identity;

import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import su.sres.securesms.R;
import su.sres.securesms.crypto.ReentrantSessionLock;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.IdentityDatabase;
import su.sres.securesms.database.model.IdentityRecord;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.util.concurrent.SimpleTask;
import su.sres.signalservice.api.SignalSessionLock;

import java.util.List;

public class UnverifiedSendDialog extends AlertDialog.Builder implements DialogInterface.OnClickListener {

  private final List<IdentityRecord> untrustedRecords;
  private final ResendListener       resendListener;

  public UnverifiedSendDialog(@NonNull Context context,
                              @NonNull String message,
                              @NonNull List<IdentityRecord> untrustedRecords,
                              @NonNull ResendListener resendListener)
  {
    super(context);
    this.untrustedRecords = untrustedRecords;
    this.resendListener   = resendListener;

    setTitle(R.string.UnverifiedSendDialog_send_message);
    setIcon(R.drawable.ic_warning);
    setMessage(message);
    setPositiveButton(R.string.UnverifiedSendDialog_send, this);
    setNegativeButton(android.R.string.cancel, null);
  }

  @Override
  public void onClick(DialogInterface dialog, int which) {
    SimpleTask.run(() -> {
      try (SignalSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
        for (IdentityRecord identityRecord : untrustedRecords) {
          ApplicationDependencies.getIdentityStore().setVerified(identityRecord.getRecipientId(),
                                                                 identityRecord.getIdentityKey(),
                                                                 IdentityDatabase.VerifiedStatus.DEFAULT);
        }
      }
      return null;
    }, nothing -> resendListener.onResendMessage());
  }

  public interface ResendListener {
    public void onResendMessage();
  }
}
