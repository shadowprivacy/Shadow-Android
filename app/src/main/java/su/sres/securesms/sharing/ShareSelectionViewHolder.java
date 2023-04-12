package su.sres.securesms.sharing;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;

import su.sres.securesms.R;
import su.sres.securesms.util.MappingAdapter;
import su.sres.securesms.util.MappingViewHolder;

public class ShareSelectionViewHolder extends MappingViewHolder<ShareSelectionMappingModel> {

    protected final @NonNull TextView name;

    public ShareSelectionViewHolder(@NonNull View itemView) {
        super(itemView);

        name = findViewById(R.id.recipient_view_name);
    }

    @Override
    public void bind(@NonNull ShareSelectionMappingModel model) {
        name.setText(model.getName(context));
    }

    public static @NonNull MappingAdapter.Factory<ShareSelectionMappingModel> createFactory(@LayoutRes int layout) {
        return new MappingAdapter.LayoutFactory<>(ShareSelectionViewHolder::new, layout);
    }
}
