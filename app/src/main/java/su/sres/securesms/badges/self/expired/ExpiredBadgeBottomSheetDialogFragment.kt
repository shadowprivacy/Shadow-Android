package su.sres.securesms.badges.self.expired

import androidx.fragment.app.FragmentManager
import androidx.navigation.fragment.findNavController
import su.sres.core.util.DimensionUnit
import su.sres.securesms.R
import su.sres.securesms.badges.models.Badge
import su.sres.securesms.badges.models.ExpiredBadge
import su.sres.securesms.components.settings.DSLConfiguration
import su.sres.securesms.components.settings.DSLSettingsAdapter
import su.sres.securesms.components.settings.DSLSettingsBottomSheetFragment
import su.sres.securesms.components.settings.DSLSettingsText
import su.sres.securesms.components.settings.app.AppSettingsActivity
import su.sres.securesms.components.settings.configure
import su.sres.securesms.keyvalue.SignalStore
import su.sres.securesms.util.BottomSheetUtil

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
    val badge: Badge = ExpiredBadgeBottomSheetDialogFragmentArgs.fromBundle(requireArguments()).badge
    val isLikelyASustainer = SignalStore.donationsValues().isLikelyASustainer()

    return configure {
      customPref(ExpiredBadge.Model(badge))

      sectionHeaderPref(
        DSLSettingsText.from(
          if (badge.isBoost()) {
            R.string.ExpiredBadgeBottomSheetDialogFragment__your_badge_has_expired
          } else {
            R.string.ExpiredBadgeBottomSheetDialogFragment__subscription_cancelled
          },
          DSLSettingsText.CenterModifier
        )
      )

      space(DimensionUnit.DP.toPixels(4f).toInt())

      noPadTextPref(
        DSLSettingsText.from(
          if (badge.isBoost()) {
            getString(R.string.ExpiredBadgeBottomSheetDialogFragment__your_boost_badge_has_expired)
          } else {
            getString(R.string.ExpiredBadgeBottomSheetDialogFragment__your_sustainer)
          },
          DSLSettingsText.CenterModifier
        )
      )

      space(DimensionUnit.DP.toPixels(16f).toInt())

      noPadTextPref(
        DSLSettingsText.from(
          if (badge.isBoost()) {
            if (isLikelyASustainer) {
              R.string.ExpiredBadgeBottomSheetDialogFragment__you_can_reactivate
            } else {
              R.string.ExpiredBadgeBottomSheetDialogFragment__to_continue_supporting_technology
            }
          } else {
            R.string.ExpiredBadgeBottomSheetDialogFragment__you_can
          },
          DSLSettingsText.CenterModifier
        )
      )

      space(DimensionUnit.DP.toPixels(92f).toInt())

      primaryButton(
        text = DSLSettingsText.from(
          if (badge.isBoost()) {
            if (isLikelyASustainer) {
              R.string.ExpiredBadgeBottomSheetDialogFragment__add_a_boost
            } else {
              R.string.ExpiredBadgeBottomSheetDialogFragment__become_a_sustainer
            }
          } else {
            R.string.ExpiredBadgeBottomSheetDialogFragment__renew_subscription
          }
        ),
        onClick = {
          dismiss()
          if (isLikelyASustainer) {
            requireActivity().startActivity(AppSettingsActivity.boost(requireContext()))
          } else {
            requireActivity().startActivity(AppSettingsActivity.subscriptions(requireContext()))
          }
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

  companion object {
    @JvmStatic
    fun show(badge: Badge, fragmentManager: FragmentManager) {
      val args = ExpiredBadgeBottomSheetDialogFragmentArgs.Builder(badge).build()
      val fragment = ExpiredBadgeBottomSheetDialogFragment()
      fragment.arguments = args.toBundle()
      fragment.show(fragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
    }
  }
}