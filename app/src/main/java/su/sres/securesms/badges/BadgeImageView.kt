package su.sres.securesms.badges

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.annotation.ColorInt
import androidx.annotation.Px
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.res.use
import androidx.lifecycle.Lifecycle
import su.sres.core.util.logging.Log
import su.sres.securesms.R
import su.sres.securesms.badges.Badges.insetWithOutline
import su.sres.securesms.badges.models.Badge
import su.sres.securesms.mms.GlideApp
import su.sres.securesms.recipients.Recipient
import su.sres.securesms.util.ViewUtil
import su.sres.securesms.util.visible

private val TAG = Log.tag(BadgeImageView::class.java)

class BadgeImageView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

  @Px
  private var outlineWidth: Float = 0f

  @ColorInt
  private var outlineColor: Int = Color.BLACK

  init {
    context.obtainStyledAttributes(attrs, R.styleable.BadgeImageView).use {
      outlineWidth = it.getDimension(R.styleable.BadgeImageView_badge_outline_width, 0f)
      outlineColor = it.getColor(R.styleable.BadgeImageView_badge_outline_color, Color.BLACK)
    }
  }

  fun setBadgeFromRecipient(recipient: Recipient?) {
    if (recipient == null || recipient.badges.isEmpty()) {
      setBadge(null)
    } else {
      setBadge(recipient.badges[0])
    }
  }

  fun setBadge(badge: Badge?) {
    visible = badge != null

    val lifecycle = ViewUtil.getActivityLifecycle(this)
    if (lifecycle?.currentState == Lifecycle.State.DESTROYED) {
      Log.w(TAG, "Ignoring setBadge call for destroyed activity.")
      return
    }

    GlideApp
      .with(this)
      .load(badge)
      .into(this)
  }

  override fun setImageDrawable(drawable: Drawable?) {
    if (drawable == null || outlineWidth == 0f) {
      super.setImageDrawable(drawable)
    } else {
      super.setImageDrawable(
        drawable.insetWithOutline(
          outlineWidth, outlineColor
        )
      )
    }
  }
}