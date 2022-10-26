package su.sres.securesms.conversation.ui.mentions;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import su.sres.securesms.R;
import su.sres.securesms.util.MappingAdapter;
import su.sres.securesms.util.MappingModel;
import su.sres.securesms.util.viewholders.RecipientViewHolder;

public class MentionsPickerAdapter extends MappingAdapter {
    private final Runnable currentListChangedListener;

    public MentionsPickerAdapter(@Nullable RecipientViewHolder.EventListener<MentionViewState> listener, @NonNull Runnable currentListChangedListener) {
        this.currentListChangedListener = currentListChangedListener;
        registerFactory(MentionViewState.class, RecipientViewHolder.createFactory(R.layout.mentions_picker_recipient_list_item, listener));
    }

    @Override
    public void onCurrentListChanged(@NonNull List<MappingModel<?>> previousList, @NonNull List<MappingModel<?>> currentList) {
        super.onCurrentListChanged(previousList, currentList);
        currentListChangedListener.run();
    }
}