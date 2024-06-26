package su.sres.securesms.mediasend.v2.review

import android.animation.Animator
import android.animation.ObjectAnimator
import android.view.View
import androidx.core.animation.doOnEnd
import su.sres.securesms.util.ViewUtil
import su.sres.securesms.util.visible

object MediaReviewAnimatorController {

  fun getSlideInAnimator(view: View): Animator {
    return ObjectAnimator.ofFloat(view, "translationY", view.translationY, 0f)
  }

  fun getSlideOutAnimator(view: View): Animator {
    return ObjectAnimator.ofFloat(view, "translationY", view.translationX, ViewUtil.dpToPx(48).toFloat())
  }

  fun getFadeInAnimator(view: View): Animator {
    view.visible = true
    view.isEnabled = true

    return ObjectAnimator.ofFloat(view, "alpha", view.alpha, 1f)
  }

  fun getFadeOutAnimator(view: View): Animator {
    view.isEnabled = false

    val animator = ObjectAnimator.ofFloat(view, "alpha", view.alpha, 0f)

    animator.doOnEnd { view.visible = false }

    return animator
  }
}