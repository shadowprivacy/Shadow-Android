package su.sres.securesms.conversation.ui.mentions;

import androidx.annotation.Nullable;

import su.sres.securesms.conversation.ui.mentions.MentionViewHolder.MentionEventsListener;
import su.sres.securesms.util.MappingAdapter;

public class MentionsPickerAdapter extends MappingAdapter {
    public MentionsPickerAdapter(@Nullable MentionEventsListener mentionEventsListener) {
        registerFactory(MentionViewState.class, MentionViewHolder.createFactory(mentionEventsListener));
    }
}