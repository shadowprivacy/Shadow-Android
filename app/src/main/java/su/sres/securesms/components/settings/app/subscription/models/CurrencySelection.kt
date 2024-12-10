package su.sres.securesms.components.settings.app.subscription.models

import android.view.View
import android.widget.TextView
import su.sres.securesms.R
import su.sres.securesms.components.settings.PreferenceModel
import su.sres.securesms.util.MappingAdapter
import su.sres.securesms.util.MappingViewHolder
import java.util.Currency

object CurrencySelection {

  fun register(adapter: MappingAdapter) {
    adapter.registerFactory(Model::class.java, MappingAdapter.LayoutFactory({ ViewHolder(it) }, R.layout.subscription_currency_selection))
  }

  class Model(
    val selectedCurrency: Currency,
    override val isEnabled: Boolean,
    val onClick: () -> Unit
  ) : PreferenceModel<Model>(isEnabled = isEnabled) {
    override fun areItemsTheSame(newItem: Model): Boolean {
      return true
    }

    override fun areContentsTheSame(newItem: Model): Boolean {
      return super.areContentsTheSame(newItem) &&
        newItem.selectedCurrency == selectedCurrency
    }
  }

  class ViewHolder(itemView: View) : MappingViewHolder<Model>(itemView) {

    private val spinner: TextView = itemView.findViewById(R.id.subscription_currency_selection_spinner)

    override fun bind(model: Model) {
      spinner.text = model.selectedCurrency.currencyCode

      itemView.setOnClickListener { model.onClick() }

      itemView.isEnabled = model.isEnabled
      itemView.isClickable = model.isEnabled
    }
  }
}