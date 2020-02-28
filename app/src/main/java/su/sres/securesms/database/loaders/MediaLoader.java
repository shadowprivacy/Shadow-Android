package su.sres.securesms.database.loaders;

import android.content.Context;

import su.sres.securesms.util.AbstractCursorLoader;

public abstract class MediaLoader extends AbstractCursorLoader {

    MediaLoader(Context context) {
        super(context);
    }

    public enum MediaType {
        GALLERY,
        DOCUMENT,
        AUDIO,
        ALL
    }
}