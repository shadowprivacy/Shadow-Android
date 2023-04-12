package su.sres.securesms.sharing.interstitial;

import su.sres.securesms.R;
import su.sres.securesms.util.MappingAdapter;
import su.sres.securesms.util.viewholders.RecipientViewHolder;

class ShareInterstitialSelectionAdapter extends MappingAdapter {
    ShareInterstitialSelectionAdapter() {
        registerFactory(ShareInterstitialMappingModel.class, RecipientViewHolder.createFactory(R.layout.share_contact_selection_item, null));
    }
}
