package su.sres.securesms.components.webrtc.participantslist;

import android.view.View;

import androidx.annotation.NonNull;

import su.sres.securesms.util.viewholders.RecipientViewHolder;

public class CallParticipantViewHolder extends RecipientViewHolder<CallParticipantViewState> {
    public CallParticipantViewHolder(@NonNull View itemView) {
        super(itemView, null);
    }

    @Override
    public void bind(@NonNull CallParticipantViewState model) {
        super.bind(model);
    }
}
