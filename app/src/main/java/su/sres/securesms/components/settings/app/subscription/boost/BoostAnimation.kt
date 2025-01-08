package su.sres.securesms.components.settings.app.subscription.boost

import android.animation.Animator
import android.view.View
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import su.sres.securesms.R
import su.sres.securesms.animation.AnimationCompleteListener
import su.sres.securesms.components.settings.PreferenceModel
import su.sres.securesms.util.MappingAdapter
import su.sres.securesms.util.MappingViewHolder

/**
 * A simple mapping model to show a boost animation.
 */
object BoostAnimation {

  class Model : PreferenceModel<Model>(isEnabled = true) {
    override fun areItemsTheSame(newItem: Model): Boolean = true
  }

  class ViewHolder(itemView: View) : MappingViewHolder<Model>(itemView) {

    private val lottie: LottieAnimationView = findViewById(R.id.boost_animation_view)

    override fun bind(model: Model) {
      lottie.playAnimation()
      lottie.addAnimatorListener(object : AnimationCompleteListener() {
        override fun onAnimationEnd(animation: Animator?) {
          lottie.removeAnimatorListener(this)
          lottie.setMinAndMaxFrame(30, 91)
          lottie.repeatMode = LottieDrawable.RESTART
          lottie.repeatCount = LottieDrawable.INFINITE
          lottie.frame = 30
          lottie.playAnimation()
        }
      })
    }
  }

  fun register(adapter: MappingAdapter) {
    adapter.registerFactory(Model::class.java, MappingAdapter.LayoutFactory({ ViewHolder(it) }, R.layout.boost_animation_pref))
  }
}