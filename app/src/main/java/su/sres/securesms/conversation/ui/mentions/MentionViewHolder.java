package su.sres.securesms.conversation.ui.mentions;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import su.sres.securesms.R;
import su.sres.securesms.components.AvatarImageView;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.util.MappingAdapter;
import su.sres.securesms.util.MappingViewHolder;

public class MentionViewHolder extends MappingViewHolder<MentionViewState> {

    private final AvatarImageView avatar;
    private final TextView        name;
    @Nullable private final MentionEventsListener mentionEventsListener;

    public MentionViewHolder(@NonNull View itemView, @Nullable MentionEventsListener mentionEventsListener) {
        super(itemView);
        this.mentionEventsListener = mentionEventsListener;

        avatar   = findViewById(R.id.mention_recipient_avatar);
        name     = findViewById(R.id.mention_recipient_name);
    }

    @Override
    public void bind(@NonNull MentionViewState model) {
        avatar.setRecipient(model.getRecipient());
        name.setText(model.getName(context));
        itemView.setOnClickListener(v -> {
            if (mentionEventsListener != null) {
                mentionEventsListener.onMentionClicked(model.getRecipient());
            }
        });
    }

    public interface MentionEventsListener {
        void onMentionClicked(@NonNull Recipient recipient);
    }

    public static MappingAdapter.Factory<MentionViewState> createFactory(@Nullable MentionEventsListener mentionEventsListener) {
        return new MappingAdapter.LayoutFactory<>(view -> new MentionViewHolder(view, mentionEventsListener), R.layout.mentions_picker_recipient_list_item);
    }
}