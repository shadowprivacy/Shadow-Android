package su.sres.securesms.database;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import su.sres.securesms.R;
import su.sres.securesms.components.emoji.EmojiStrings;
import su.sres.securesms.contactshare.Contact;
import su.sres.securesms.contactshare.ContactUtil;
import su.sres.securesms.database.model.MessageRecord;
import su.sres.securesms.database.model.MmsMessageRecord;
import su.sres.securesms.logging.Log;
import su.sres.securesms.mms.GifSlide;
import su.sres.securesms.mms.Slide;
import su.sres.securesms.util.MessageRecordUtil;

public final class ThreadBodyUtil {

    private static final String TAG = Log.tag(ThreadBodyUtil.class);

    private ThreadBodyUtil() {
    }

    public static @NonNull String getFormattedBodyFor(@NonNull Context context, @NonNull MessageRecord record) {
        if (record.isMms()) {
            return getFormattedBodyForMms(context, (MmsMessageRecord) record);
        }

        return record.getBody();
    }

    private static @NonNull String getFormattedBodyForMms(@NonNull Context context, @NonNull MmsMessageRecord record) {
        if (record.getSharedContacts().size() > 0) {
            Contact contact = record.getSharedContacts().get(0);

            return ContactUtil.getStringSummary(context, contact).toString();
        } else if (record.getSlideDeck().getDocumentSlide() != null) {
            return format(context, record, EmojiStrings.FILE, R.string.ThreadRecord_file);
        } else if (record.getSlideDeck().getAudioSlide() != null) {
            return format(context, record, EmojiStrings.AUDIO, R.string.ThreadRecord_voice_message);
        } else if (MessageRecordUtil.hasSticker(record)) {
            return format(context, record, EmojiStrings.STICKER, R.string.ThreadRecord_sticker);
        }

        boolean hasImage = false;
        boolean hasVideo = false;
        boolean hasGif   = false;

        for (Slide slide : record.getSlideDeck().getSlides()) {
            hasVideo |= slide.hasVideo();
            hasImage |= slide.hasImage();
            hasGif   |= slide instanceof GifSlide;
        }

        if (hasGif) {
            return format(context, record, EmojiStrings.GIF, R.string.ThreadRecord_gif);
        } else if (hasVideo) {
            return format(context, record, EmojiStrings.VIDEO, R.string.ThreadRecord_video);
        } else if (hasImage) {
            return format(context, record, EmojiStrings.PHOTO, R.string.ThreadRecord_photo);
        } else if (TextUtils.isEmpty(record.getBody())) {
            Log.w(TAG, "Got a media message without a body of a type we were not able to process. [contains media slide]:" + record.containsMediaSlide());
            return context.getString(R.string.ThreadRecord_media_message);
        } else {
            Log.w(TAG, "Got a media message with a body of a type we were not able to process. [contains media slide]:" + record.containsMediaSlide());
            return getBody(context, record);
        }
    }

    private static @NonNull String format(@NonNull Context context, @NonNull MessageRecord record, @NonNull String emoji, @StringRes int defaultStringRes) {
        return String.format("%s %s", emoji, getBodyOrDefault(context, record, defaultStringRes));
    }

    private static @NonNull String getBodyOrDefault(@NonNull Context context, @NonNull MessageRecord record, @StringRes int defaultStringRes) {
        return TextUtils.isEmpty(record.getBody()) ? context.getString(defaultStringRes) : getBody(context, record);
    }

    private static @NonNull String getBody(@NonNull Context context, @NonNull MessageRecord record) {
        return MentionUtil.updateBodyWithDisplayNames(context, record, record.getBody()).toString();
    }
}