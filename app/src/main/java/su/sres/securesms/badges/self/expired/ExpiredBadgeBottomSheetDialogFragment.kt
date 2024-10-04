package su.sres.securesms.badges.self.expired

import androidx.navigation.fragment.findNavController
import su.sres.core.util.DimensionUnit
import su.sres.securesms.R
import su.sres.securesms.badges.models.ExpiredBadge
import su.sres.securesms.components.settings.DSLConfiguration
import su.sres.securesms.components.settings.DSLSettingsAdapter
import su.sres.securesms.components.settings.DSLSettingsBottomSheetFragment
import su.sres.securesms.components.settings.DSLSettingsText
import su.sres.securesms.components.settings.configure

/**
 * Bottom sheet displaying a fading badge with a notice and action for becoming a subscriber again.
 */
class ExpiredBadgeBottomSheetDialogFragment : DSLSettingsBottomSheetFragment(
  peekHeightPercentage = 1f
) {
  override fun bindAdapter(adapter: DSLSettingsAdapter) {
    ExpiredBadge.register(adapter)

    adapter.submitList(getConfiguration().toMappingModelList())
  }

  private fun getConfiguration(): DSLConfiguration {
    val badge = ExpiredBadgeBottomSheetDialogFragmentArgs.fromBundle(requireArguments()).badge

    return configure {
      customPref(ExpiredBadge.Model(badge))

      sectionHeaderPref(R.string.ExpiredBadgeBottomSheetDialogFragment__your_badge_has_expired)

      space(DimensionUnit.DP.toPixels(4f).toInt())

      noPadTextPref(
        DSLSettingsText.from(
          getString(R.string.ExpiredBadgeBottomSheetDialogFragment__your_s_badge_has_expired, badge.name),
          DSLSettingsText.CenterModifier
        )
      )

      space(DimensionUnit.DP.toPixels(16f).toInt())

      noPadTextPref(
        DSLSettingsText.from(
          R.string.ExpiredBadgeBottomSheetDialogFragment__to_continue_supporting,
          DSLSettingsText.CenterModifier
        )
      )

      space(DimensionUnit.DP.toPixels(92f).toInt())

      primaryButton(
        text = DSLSettingsText.from(R.string.ExpiredBadgeBottomSheetDialogFragment__become_a_subscriber),
        onClick = {
          dismiss()
          findNavController().navigate(R.id.action_directly_to_subscribe)
        }
      )

      secondaryButtonNoOutline(
        text = DSLSettingsText.from(R.string.ExpiredBadgeBottomSheetDialogFragment__not_now),
        onClick = {
          dismiss()
        }
      )
    }
  }
}