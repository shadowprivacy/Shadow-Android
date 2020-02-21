package su.sres.securesms.util;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import com.bumptech.glide.load.engine.DiskCacheStrategy;

import su.sres.securesms.R;
import su.sres.securesms.color.MaterialColor;
import su.sres.securesms.contacts.avatars.ContactColors;
import su.sres.securesms.contacts.avatars.GeneratedContactPhoto;
import su.sres.securesms.contacts.avatars.ProfileContactPhoto;
import su.sres.securesms.mms.GlideApp;
import su.sres.securesms.recipients.Recipient;
import org.whispersystems.libsignal.util.guava.Optional;

public final class AvatarUtil {

    private AvatarUtil() {
    }

    public static void loadIconIntoImageView(@NonNull Recipient recipient, @NonNull ImageView target) {
        Context       context       = target.getContext();
        String        name          = Optional.fromNullable(recipient.getDisplayName(context)).or(Optional.fromNullable(TextSecurePreferences.getProfileName(context))).or("");
        MaterialColor fallbackColor = recipient.getColor();

        if (fallbackColor == ContactColors.UNKNOWN_COLOR && !TextUtils.isEmpty(name)) {
            fallbackColor = ContactColors.generateFor(name);
        }

        Drawable fallback = new GeneratedContactPhoto(name, R.drawable.ic_profile_outline_40).asDrawable(context, fallbackColor.toAvatarColor(context));

        GlideApp.with(context)
                .load(new ProfileContactPhoto(recipient.getId(), String.valueOf(TextSecurePreferences.getProfileAvatarId(context))))
                .error(fallback)
                .circleCrop()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(target);
    }
}