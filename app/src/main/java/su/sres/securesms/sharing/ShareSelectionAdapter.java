package su.sres.securesms.sharing;

import su.sres.securesms.R;
import su.sres.securesms.util.MappingAdapter;

class ShareSelectionAdapter extends MappingAdapter {
    ShareSelectionAdapter() {
        registerFactory(ShareSelectionMappingModel.class,
                ShareSelectionViewHolder.createFactory(R.layout.share_contact_selection_item));
    }
}
