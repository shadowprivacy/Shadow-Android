package su.sres.securesms.components.settings.app.subscription.currency

import androidx.fragment.app.viewModels
import su.sres.securesms.components.settings.DSLConfiguration
import su.sres.securesms.components.settings.DSLSettingsAdapter
import su.sres.securesms.components.settings.DSLSettingsBottomSheetFragment
import su.sres.securesms.components.settings.DSLSettingsText
import su.sres.securesms.components.settings.configure
import java.util.Locale

/**
 * Simple fragment for selecting a currency for Donations
 */
class SetCurrencyFragment : DSLSettingsBottomSheetFragment() {

  private val viewModel: SetCurrencyViewModel by viewModels()

  override fun bindAdapter(adapter: DSLSettingsAdapter) {
    viewModel.state.observe(viewLifecycleOwner) { state ->
      adapter.submitList(getConfiguration(state).toMappingModelList())
    }
  }

  private fun getConfiguration(state: SetCurrencyState): DSLConfiguration {
    return configure {
      state.currencies.forEach { currency ->
        radioPref(
          title = DSLSettingsText.from(currency.getDisplayName(Locale.getDefault())),
          summary = DSLSettingsText.from(currency.currencyCode),
          isChecked = currency.currencyCode == state.selectedCurrencyCode,
          onClick = {
            viewModel.setSelectedCurrency(currency.currencyCode)
          }
        )
      }
    }
  }
}