package su.sres.securesms.insights;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import com.bumptech.glide.load.engine.DiskCacheStrategy;

import su.sres.securesms.contacts.avatars.FallbackContactPhoto;
import su.sres.securesms.contacts.avatars.ProfileContactPhoto;
import su.sres.securesms.conversation.colors.AvatarColor;
import su.sres.securesms.mms.GlideApp;

class InsightsUserAvatar {
  private final ProfileContactPhoto  profileContactPhoto;
  private final AvatarColor          fallbackColor;
  private final FallbackContactPhoto fallbackContactPhoto;

  InsightsUserAvatar(@NonNull ProfileContactPhoto profileContactPhoto, @NonNull AvatarColor fallbackColor, @NonNull FallbackContactPhoto fallbackContactPhoto) {
    this.profileContactPhoto  = profileContactPhoto;
    this.fallbackColor        = fallbackColor;
    this.fallbackContactPhoto = fallbackContactPhoto;
  }

  private Drawable fallbackDrawable(@NonNull Context context) {
    return fallbackContactPhoto.asDrawable(context, fallbackColor.colorInt());
  }

  void load(ImageView into) {
    GlideApp.with(into)
            .load(profileContactPhoto)
            .error(fallbackDrawable(into.getContext()))
            .circleCrop()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .into(into);
  }
}