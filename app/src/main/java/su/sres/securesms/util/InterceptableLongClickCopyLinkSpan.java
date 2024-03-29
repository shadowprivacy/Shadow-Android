package su.sres.securesms.util;

import android.view.View;

import androidx.annotation.NonNull;

import su.sres.securesms.groups.v2.GroupInviteLinkUrl;

/**
 * Passes clicked Urls to the supplied {@link UrlClickHandler}.
 */
public final class InterceptableLongClickCopyLinkSpan extends LongClickCopySpan {

    private final UrlClickHandler onClickListener;

    public InterceptableLongClickCopyLinkSpan(@NonNull String url,
                                              @NonNull UrlClickHandler onClickListener)
    {
        super(url);
        this.onClickListener = onClickListener;
    }

    @Override
    public void onClick(View widget) {
        if (!onClickListener.handleOnClick(getURL())) {
            super.onClick(widget);
        }
    }
}