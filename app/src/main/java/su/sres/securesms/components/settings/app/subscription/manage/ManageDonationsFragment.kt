package su.sres.securesms.components.settings.app.subscription.manage

import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import su.sres.core.util.DimensionUnit
import su.sres.core.util.money.FiatMoney
import su.sres.securesms.R
import su.sres.securesms.badges.models.BadgePreview
import su.sres.securesms.components.settings.DSLConfiguration
import su.sres.securesms.components.settings.DSLSettingsAdapter
import su.sres.securesms.components.settings.DSLSettingsFragment
import su.sres.securesms.components.settings.DSLSettingsIcon
import su.sres.securesms.components.settings.DSLSettingsText
import su.sres.securesms.components.settings.app.AppSettingsActivity
import su.sres.securesms.components.settings.app.subscription.SubscriptionsRepository
import su.sres.securesms.components.settings.configure
import su.sres.securesms.components.settings.models.IndeterminateLoadingCircle
import su.sres.securesms.dependencies.ApplicationDependencies
import su.sres.securesms.help.HelpFragment
import su.sres.securesms.subscription.Subscription
import su.sres.securesms.util.LifecycleDisposable
import java.util.Currency
import java.util.concurrent.TimeUnit

/**
 * Fragment displayed when a user enters "Subscriptions" via app settings but is already
 * a subscriber. Used to manage their current subscription, view badges, and boost.
 */
class ManageDonationsFragment : DSLSettingsFragment() {

  private val viewModel: ManageDonationsViewModel by viewModels(
    factoryProducer = {
      ManageDonationsViewModel.Factory(SubscriptionsRepository(ApplicationDependencies.getDonationsService()))
    }
  )

  private val lifecycleDisposable = LifecycleDisposable()

  override fun onResume() {
    super.onResume()
    viewModel.refresh()
  }

  override fun bindAdapter(adapter: DSLSettingsAdapter) {
    ActiveSubscriptionPreference.register(adapter)
    IndeterminateLoadingCircle.register(adapter)

    BadgePreview.register(adapter)

    viewModel.state.observe(viewLifecycleOwner) { state ->
      adapter.submitList(getConfiguration(state).toMappingModelList())
    }

    lifecycleDisposable.bindTo(viewLifecycleOwner.lifecycle)
    lifecycleDisposable += viewModel.events.subscribe { event: ManageDonationsEvent ->
      when (event) {
        ManageDonationsEvent.NOT_SUBSCRIBED -> handleUserIsNotSubscribed()
        ManageDonationsEvent.ERROR_GETTING_SUBSCRIPTION -> handleErrorGettingSubscription()
      }
    }
  }

  private fun getConfiguration(state: ManageDonationsState): DSLConfiguration {
    return configure {
      /* customPref(
        BadgePreview.Model(
          badge = state.featuredBadge
        )
      )
      space(DimensionUnit.DP.toPixels(8f).toInt()) */

      sectionHeaderPref(
        title = DSLSettingsText.from(
          R.string.SubscribeFragment__signal_is_powered_by_people_like_you,
          DSLSettingsText.CenterModifier, DSLSettingsText.Title2BoldModifier
        )
      )

      space(DimensionUnit.DP.toPixels(32f).toInt())

      noPadTextPref(
        title = DSLSettingsText.from(
          R.string.ManageDonationsFragment__my_support,
          DSLSettingsText.Body1BoldModifier, DSLSettingsText.BoldModifier
        )
      )

      if (state.transactionState is ManageDonationsState.TransactionState.NotInTransaction) {
        val activeSubscription = state.transactionState.activeSubscription.activeSubscription
        if (activeSubscription != null) {
          val subscription: Subscription? = state.availableSubscriptions.firstOrNull { activeSubscription.level == it.level }
          if (subscription != null) {
            space(DimensionUnit.DP.toPixels(12f).toInt())

            val activeCurrency = Currency.getInstance(activeSubscription.currency)
            val activeAmount = activeSubscription.amount.movePointLeft(activeCurrency.defaultFractionDigits)

            customPref(
              ActiveSubscriptionPreference.Model(
                price = FiatMoney(activeAmount, activeCurrency),
                subscription = subscription,
                onAddBoostClick = {
                  findNavController().navigate(ManageDonationsFragmentDirections.actionManageDonationsFragmentToBoosts())
                },
                renewalTimestamp = TimeUnit.SECONDS.toMillis(activeSubscription.endOfCurrentPeriod),
                redemptionState = state.getRedemptionState(),
                onContactSupport = {
                  requireActivity().finish()
                  requireActivity().startActivity(AppSettingsActivity.help(requireContext(), HelpFragment.DONATION_INDEX))
                },
                activeSubscription = activeSubscription
              )
            )

            dividerPref()
          } else {
            customPref(IndeterminateLoadingCircle)
          }
        } else {
          customPref(IndeterminateLoadingCircle)
        }
      } else {
        customPref(IndeterminateLoadingCircle)
      }

      clickPref(
        title = DSLSettingsText.from(R.string.ManageDonationsFragment__manage_subscription),
        icon = DSLSettingsIcon.from(R.drawable.ic_person_white_24dp),
        isEnabled = state.getRedemptionState() != ManageDonationsState.SubscriptionRedemptionState.IN_PROGRESS,
        onClick = {
          findNavController().navigate(ManageDonationsFragmentDirections.actionManageDonationsFragmentToSubscribeFragment())
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.ManageDonationsFragment__badges),
        icon = DSLSettingsIcon.from(R.drawable.ic_badge_24),
        onClick = {
          findNavController().navigate(ManageDonationsFragmentDirections.actionManageDonationsFragmentToManageBadges())
        }
      )

      /* externalLinkPref(
        title = DSLSettingsText.from(R.string.ManageDonationsFragment__subscription_faq),
        icon = DSLSettingsIcon.from(R.drawable.ic_help_24),
        linkId = R.string.donate_url
      ) */
    }
  }

  private fun handleUserIsNotSubscribed() {
    findNavController().popBackStack()
  }

  private fun handleErrorGettingSubscription() {
    Toast.makeText(requireContext(), R.string.ManageDonationsFragment__error_getting_subscription, Toast.LENGTH_LONG).show()
  }
}