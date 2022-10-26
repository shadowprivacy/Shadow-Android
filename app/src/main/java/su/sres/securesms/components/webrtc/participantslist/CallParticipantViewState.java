package su.sres.securesms.components.webrtc.participantslist;

import androidx.annotation.NonNull;

import su.sres.securesms.events.CallParticipant;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.util.viewholders.RecipientMappingModel;

public final class CallParticipantViewState extends RecipientMappingModel<CallParticipantViewState> {

    private final CallParticipant callParticipant;

    CallParticipantViewState(@NonNull CallParticipant callParticipant) {
        this.callParticipant = callParticipant;
    }

    @Override
    public @NonNull Recipient getRecipient() {
        return callParticipant.getRecipient();
    }
}
