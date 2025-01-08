package su.sres.securesms.badges.self.overview

import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import su.sres.securesms.R
import su.sres.securesms.badges.BadgeRepository
import su.sres.securesms.badges.Badges
import su.sres.securesms.badges.Badges.displayBadges
import su.sres.securesms.badges.models.Badge
import su.sres.securesms.badges.view.ViewBadgeBottomSheetDialogFragment
import su.sres.securesms.components.settings.DSLConfiguration
import su.sres.securesms.components.settings.DSLSettingsAdapter
import su.sres.securesms.components.settings.DSLSettingsFragment
import su.sres.securesms.components.settings.DSLSettingsText
import su.sres.securesms.components.settings.app.subscription.SubscriptionsRepository
import su.sres.securesms.components.settings.configure
import su.sres.securesms.dependencies.ApplicationDependencies
import su.sres.securesms.recipients.Recipient
import su.sres.securesms.util.LifecycleDisposable

/**
 * Fragment to allow user to manage options related to the badges they've unlocked.
 */
class BadgesOverviewFragment : DSLSettingsFragment(
  titleId = R.string.ManageProfileFragment_badges,
  layoutManagerProducer = Badges::createLayoutManagerForGridWithBadges
) {

  private val lifecycleDisposable = LifecycleDisposable()
  private val viewModel: BadgesOverviewViewModel by viewModels(
    factoryProducer = {
      BadgesOverviewViewModel.Factory(BadgeRepository(requireContext()), SubscriptionsRepository(ApplicationDependencies.getDonationsService()))
    }
  )

  override fun bindAdapter(adapter: DSLSettingsAdapter) {
    Badge.register(adapter) { badge, _, isFaded ->
      if (badge.isExpired() || isFaded) {
        findNavController().navigate(BadgesOverviewFragmentDirections.actionBadgeManageFragmentToExpiredBadgeDialog(badge))
      } else {
        ViewBadgeBottomSheetDialogFragment.show(parentFragmentManager, Recipient.self().id, badge)
      }
    }

    lifecycleDisposable.bindTo(viewLifecycleOwner.lifecycle)

    viewModel.state.observe(viewLifecycleOwner) { state ->
      adapter.submitList(getConfiguration(state).toMappingModelList())
    }

    lifecycleDisposable.add(
      viewModel.events.subscribe { event: BadgesOverviewEvent ->
        when (event) {
          BadgesOverviewEvent.FAILED_TO_UPDATE_PROFILE -> Toast.makeText(requireContext(), R.string.BadgesOverviewFragment__failed_to_update_profile, Toast.LENGTH_LONG).show()
        }
      }
    )
  }

  private fun getConfiguration(state: BadgesOverviewState): DSLConfiguration {
    return configure {
      sectionHeaderPref(R.string.BadgesOverviewFragment__my_badges)

      displayBadges(
        context = requireContext(),
        badges = state.allUnlockedBadges,
        fadedBadgeId = state.fadedBadgeId
      )

      asyncSwitchPref(
        title = DSLSettingsText.from(R.string.BadgesOverviewFragment__display_badges_on_profile),
        isChecked = state.displayBadgesOnProfile,
        isEnabled = state.stage == BadgesOverviewState.Stage.READY && state.hasUnexpiredBadges && state.hasInternet,
        isProcessing = state.stage == BadgesOverviewState.Stage.UPDATING_BADGE_DISPLAY_STATE,
        onClick = {
          viewModel.setDisplayBadgesOnProfile(!state.displayBadgesOnProfile)
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.BadgesOverviewFragment__featured_badge),
        summary = state.featuredBadge?.name?.let { DSLSettingsText.from(it) },
        isEnabled = state.stage == BadgesOverviewState.Stage.READY && state.hasUnexpiredBadges && state.hasInternet,
        onClick = {
          findNavController().navigate(BadgesOverviewFragmentDirections.actionBadgeManageFragmentToFeaturedBadgeFragment())
        }
      )
    }
  }
}