package su.sres.securesms.components.settings.app.subscription.models

import android.view.View
import su.sres.securesms.R
import su.sres.securesms.components.settings.PreferenceModel
import su.sres.securesms.util.MappingAdapter
import su.sres.securesms.util.MappingViewHolder

object GooglePayButton {

  class Model(val onClick: () -> Unit, override val isEnabled: Boolean) : PreferenceModel<Model>(isEnabled = isEnabled) {
    override fun areItemsTheSame(newItem: Model): Boolean = true
  }

  class ViewHolder(itemView: View) : MappingViewHolder<Model>(itemView) {

    private val googlePayButton: View = findViewById(R.id.googlepay_button)

    override fun bind(model: Model) {
      googlePayButton.isEnabled = model.isEnabled
      googlePayButton.setOnClickListener {
        googlePayButton.isEnabled = false
        model.onClick()
      }
    }
  }

  fun register(adapter: MappingAdapter) {
    adapter.registerFactory(Model::class.java, MappingAdapter.LayoutFactory({ ViewHolder(it) }, R.layout.google_pay_button_pref))
  }
}