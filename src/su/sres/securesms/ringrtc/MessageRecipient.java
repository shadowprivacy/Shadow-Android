package su.sres.securesms.ringrtc;

import android.content.Context;
import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.List;

import su.sres.ringrtc.SignalMessageRecipient;

import su.sres.securesms.crypto.UnidentifiedAccessUtil;
import su.sres.securesms.logging.Log;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.recipients.RecipientUtil;

import su.sres.signalservice.api.SignalServiceMessageSender;
import su.sres.signalservice.api.crypto.UntrustedIdentityException;
import su.sres.signalservice.api.messages.calls.AnswerMessage;
import su.sres.signalservice.api.messages.calls.IceUpdateMessage;
import su.sres.signalservice.api.messages.calls.HangupMessage;
import su.sres.signalservice.api.messages.calls.OfferMessage;
import su.sres.signalservice.api.messages.calls.SignalServiceCallMessage;
import su.sres.signalservice.api.push.SignalServiceAddress;

public final class MessageRecipient implements SignalMessageRecipient {

    private static final String TAG = Log.tag(MessageRecipient.class);

    @NonNull private final Recipient recipient;
    @NonNull private final SignalServiceMessageSender messageSender;

    public MessageRecipient(@NonNull SignalServiceMessageSender messageSender,
                            @NonNull Recipient                  recipient)
    {
        this.recipient     = recipient;
        this.messageSender = messageSender;
    }

    public @NonNull RecipientId getId() {
        return recipient.getId();
    }

    @Override
    public boolean isEqual(@NonNull SignalMessageRecipient inRecipient) {
        if (!(inRecipient instanceof MessageRecipient)) {
            return false;
        }

        if (getClass() != inRecipient.getClass()) {
            Log.e(TAG, "CLASSES NOT EQUAL: " + getClass().toString() + ", " + recipient.getClass().toString());
            return false;
        }

        MessageRecipient that = (MessageRecipient) inRecipient;

        return recipient.equals(that.recipient);
    }

    private void sendMessage(Context context, SignalServiceCallMessage callMessage)
            throws UntrustedIdentityException, IOException
    {
        messageSender.sendCallMessage(RecipientUtil.toSignalServiceAddress(context, recipient),
                UnidentifiedAccessUtil.getAccessFor(context, recipient),
                callMessage);
    }

    @Override
    public void sendOfferMessage(Context context, long callId, String description)
            throws UntrustedIdentityException, IOException
    {
        Log.i(TAG, "MessageRecipient::sendOfferMessage(): callId: 0x" + Long.toHexString(callId));

        OfferMessage offerMessage = new OfferMessage(callId, description);
        sendMessage(context, SignalServiceCallMessage.forOffer(offerMessage));
    }

    @Override
    public void sendAnswerMessage(Context context, long callId, String description)
            throws UntrustedIdentityException, IOException
    {
        Log.i(TAG, "MessageRecipient::sendAnswerMessage(): callId: 0x" + Long.toHexString(callId));

        AnswerMessage answerMessage = new AnswerMessage(callId, description);
        sendMessage(context, SignalServiceCallMessage.forAnswer(answerMessage));
    }

    @Override
    public void sendIceUpdates(Context context, List<IceUpdateMessage> iceUpdateMessages)
            throws UntrustedIdentityException, IOException
    {
        Log.i(TAG, "MessageRecipient::sendIceUpdates(): iceUpdates: " + iceUpdateMessages.size());

        sendMessage(context, SignalServiceCallMessage.forIceUpdates(iceUpdateMessages));
    }

    @Override
    public void sendHangupMessage(Context context, long callId)
            throws UntrustedIdentityException, IOException
    {
        Log.i(TAG, "MessageRecipient::sendHangupMessage(): callId: 0x" + Long.toHexString(callId));

        HangupMessage hangupMessage = new HangupMessage(callId);
        sendMessage(context, SignalServiceCallMessage.forHangup(hangupMessage));
    }

}