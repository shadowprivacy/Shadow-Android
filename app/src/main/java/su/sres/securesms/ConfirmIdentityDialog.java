package su.sres.securesms;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import androidx.appcompat.app.AlertDialog;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

import su.sres.core.util.logging.Log;
import su.sres.securesms.crypto.ReentrantSessionLock;
import su.sres.securesms.crypto.storage.TextSecureIdentityKeyStore;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.MessageDatabase;
import su.sres.securesms.database.documents.IdentityKeyMismatch;
import su.sres.securesms.database.model.MessageRecord;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobs.PushDecryptMessageJob;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.recipients.RecipientUtil;
import su.sres.securesms.sms.MessageSender;
import su.sres.securesms.util.Base64;
import su.sres.securesms.util.VerifySpan;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.util.guava.Optional;

import su.sres.signalservice.api.SignalSessionLock;
import su.sres.signalservice.api.messages.SignalServiceEnvelope;
import su.sres.signalservice.internal.push.SignalServiceProtos;

import java.io.IOException;

public class ConfirmIdentityDialog extends AlertDialog {

  @SuppressWarnings("unused")
  private static final String TAG = Log.tag(ConfirmIdentityDialog.class);

  private OnClickListener callback;

  public ConfirmIdentityDialog(Context context,
                               MessageRecord messageRecord,
                               IdentityKeyMismatch mismatch)
  {
    super(context);

      Recipient       recipient       = Recipient.resolved(mismatch.getRecipientId(context));
      String          name            = recipient.getDisplayName(context);
      String          introduction    = context.getString(R.string.ConfirmIdentityDialog_your_safety_number_with_s_has_changed, name, name);
      SpannableString spannableString = new SpannableString(introduction + " " +
                                                            context.getString(R.string.ConfirmIdentityDialog_you_may_wish_to_verify_your_safety_number_with_this_contact));

      spannableString.setSpan(new VerifySpan(context, mismatch),
                              introduction.length()+1, spannableString.length(),
                              Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

      setTitle(name);
      setMessage(spannableString);

      setButton(AlertDialog.BUTTON_POSITIVE, context.getString(R.string.ConfirmIdentityDialog_accept), new AcceptListener(messageRecord, mismatch, recipient.getId()));
      setButton(AlertDialog.BUTTON_NEGATIVE, context.getString(android.R.string.cancel),               new CancelListener());
  }

  @Override
  public void show() {
    super.show();
    ((TextView)this.findViewById(android.R.id.message))
                   .setMovementMethod(LinkMovementMethod.getInstance());
  }

  public void setCallback(OnClickListener callback) {
    this.callback = callback;
  }

  private class AcceptListener implements OnClickListener {

    private final MessageRecord       messageRecord;
    private final IdentityKeyMismatch mismatch;
    private final RecipientId         recipientId;

    private AcceptListener(MessageRecord messageRecord, IdentityKeyMismatch mismatch, RecipientId recipientId) {
      this.messageRecord = messageRecord;
      this.mismatch      = mismatch;
      this.recipientId   = recipientId;
    }

    @SuppressLint("StaticFieldLeak")
    @Override
    public void onClick(DialogInterface dialog, int which) {
      new AsyncTask<Void, Void, Void>()
      {
        @Override
        protected Void doInBackground(Void... params) {
          try (SignalSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
            SignalProtocolAddress      mismatchAddress  = new SignalProtocolAddress(Recipient.resolved(recipientId).requireServiceId(), 1);
            TextSecureIdentityKeyStore identityKeyStore = new TextSecureIdentityKeyStore(getContext());

            identityKeyStore.saveIdentity(mismatchAddress, mismatch.getIdentityKey(), true);
          }

          processMessageRecord(messageRecord);

          return null;
        }

        private void processMessageRecord(MessageRecord messageRecord) {
          if (messageRecord.isOutgoing()) processOutgoingMessageRecord(messageRecord);
          else                            processIncomingMessageRecord(messageRecord);
        }

        private void processOutgoingMessageRecord(MessageRecord messageRecord) {
          MessageDatabase smsDatabase = DatabaseFactory.getSmsDatabase(getContext());
          MessageDatabase mmsDatabase = DatabaseFactory.getMmsDatabase(getContext());

          if (messageRecord.isMms()) {
            mmsDatabase.removeMismatchedIdentity(messageRecord.getId(),
                                                 mismatch.getRecipientId(getContext()),
                                                 mismatch.getIdentityKey());

            if (messageRecord.getRecipient().isPushGroup()) {
              MessageSender.resendGroupMessage(getContext(), messageRecord, Recipient.resolved(mismatch.getRecipientId(getContext())).getId());
            } else {
              MessageSender.resend(getContext(), messageRecord);
            }
          } else {
            smsDatabase.removeMismatchedIdentity(messageRecord.getId(),
                    mismatch.getRecipientId(getContext()),
                                                 mismatch.getIdentityKey());

            MessageSender.resend(getContext(), messageRecord);
          }
        }

        private void processIncomingMessageRecord(MessageRecord messageRecord) {
          try {
            MessageDatabase smsDatabase  = DatabaseFactory.getSmsDatabase(getContext());

            smsDatabase.removeMismatchedIdentity(messageRecord.getId(),
                    mismatch.getRecipientId(getContext()),
                                                 mismatch.getIdentityKey());

            boolean legacy = !messageRecord.isContentBundleKeyExchange();

            SignalServiceEnvelope envelope = new SignalServiceEnvelope(SignalServiceProtos.Envelope.Type.PREKEY_BUNDLE_VALUE,
                    Optional.of(RecipientUtil.toSignalServiceAddress(getContext(), messageRecord.getIndividualRecipient())),
                                                                       messageRecord.getRecipientDeviceId(),
                                                                       messageRecord.getDateSent(),
                                                                       legacy ? Base64.decode(messageRecord.getBody()) : null,
                                                                       !legacy ? Base64.decode(messageRecord.getBody()) : null,
                    0,
                    0,
                    null);

            ApplicationDependencies.getJobManager().add(new PushDecryptMessageJob(getContext(), envelope, messageRecord.getId()));
          } catch (IOException e) {
            throw new AssertionError(e);
          }
        }

      }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

      if (callback != null) callback.onClick(null, 0);
    }
  }

  private class CancelListener implements OnClickListener {
    @Override
    public void onClick(DialogInterface dialog, int which) {
      if (callback != null) callback.onClick(null, 0);
    }
  }

}
