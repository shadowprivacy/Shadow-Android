package su.sres.securesms.conversation.ui.mentions;

import androidx.annotation.NonNull;

import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.util.viewholders.RecipientMappingModel;

import java.util.Objects;

public final class MentionViewState extends RecipientMappingModel<MentionViewState> {

    private final Recipient recipient;

    public MentionViewState(@NonNull Recipient recipient) {
        this.recipient = recipient;
    }

    @Override
    public @NonNull Recipient getRecipient() {
        return recipient;
    }
}