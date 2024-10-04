package su.sres.securesms.badges

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import androidx.annotation.Px
import androidx.core.graphics.withScale
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.SimpleColorFilter
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import su.sres.securesms.R
import su.sres.securesms.badges.models.Badge
import su.sres.securesms.components.settings.DSLConfiguration
import su.sres.securesms.util.customizeOnDraw

object Badges {
  fun DSLConfiguration.displayBadges(context: Context, badges: List<Badge>, selectedBadge: Badge? = null) {
    badges
      .map { Badge.Model(it, it == selectedBadge) }
      .forEach { customPref(it) }

    val perRow = context.resources.getInteger(R.integer.badge_columns)
    val empties = (perRow - (badges.size % perRow)) % perRow
    repeat(empties) {
      customPref(Badge.EmptyModel())
    }
  }

  fun createLayoutManagerForGridWithBadges(context: Context): RecyclerView.LayoutManager {
    val layoutManager = FlexboxLayoutManager(context)

    layoutManager.flexDirection = FlexDirection.ROW
    layoutManager.alignItems = AlignItems.CENTER
    layoutManager.justifyContent = JustifyContent.CENTER

    return layoutManager
  }
}