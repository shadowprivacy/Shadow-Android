package su.sres.securesms.mms;


import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import su.sres.securesms.attachments.Attachment;
import su.sres.securesms.util.MediaUtil;

public class TextSlide extends Slide {

    public TextSlide(@NonNull Context context, @NonNull Attachment attachment) {
        super(context, attachment);
    }

    public TextSlide(@NonNull Context context, @NonNull Uri uri, @Nullable String filename, long size) {
        super(context, constructAttachmentFromUri(context, uri, MediaUtil.LONG_TEXT, size, 0, 0, true, filename, null, null, false, false));
    }
}