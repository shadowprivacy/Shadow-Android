package su.sres.securesms.components.webrtc.participantslist;

import su.sres.securesms.R;
import su.sres.securesms.util.MappingAdapter;

public class CallParticipantsListAdapter extends MappingAdapter {

    CallParticipantsListAdapter() {
        registerFactory(CallParticipantsListHeader.class, new LayoutFactory<>(CallParticipantsListHeaderViewHolder::new, R.layout.call_participants_list_header));
        registerFactory(CallParticipantViewState.class, new LayoutFactory<>(CallParticipantViewHolder::new, R.layout.call_participants_list_item));
    }

}
