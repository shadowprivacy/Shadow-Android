package su.sres.securesms.components.settings.app.subscription.subscribe

import android.graphics.Color
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import su.sres.core.util.DimensionUnit
import su.sres.core.util.logging.Log
import su.sres.securesms.R
import su.sres.securesms.badges.models.Badge
import su.sres.securesms.badges.models.BadgePreview
import su.sres.securesms.components.settings.DSLConfiguration
import su.sres.securesms.components.settings.DSLSettingsAdapter
import su.sres.securesms.components.settings.DSLSettingsFragment
import su.sres.securesms.components.settings.DSLSettingsIcon
import su.sres.securesms.components.settings.DSLSettingsText
import su.sres.securesms.components.settings.app.AppSettingsActivity
import su.sres.securesms.components.settings.app.subscription.DonationEvent
import su.sres.securesms.components.settings.app.subscription.DonationExceptions
import su.sres.securesms.components.settings.app.subscription.DonationPaymentComponent
import su.sres.securesms.components.settings.app.subscription.SubscriptionsRepository
import su.sres.securesms.components.settings.app.subscription.models.CurrencySelection
import su.sres.securesms.components.settings.app.subscription.models.GooglePayButton
import su.sres.securesms.components.settings.app.subscription.models.NetworkFailure
import su.sres.securesms.components.settings.configure
import su.sres.securesms.components.settings.models.Progress
import su.sres.securesms.dependencies.ApplicationDependencies
import su.sres.securesms.help.HelpFragment
import su.sres.securesms.keyboard.findListener
import su.sres.securesms.payments.FiatMoneyUtil
import su.sres.securesms.subscription.Subscription
import su.sres.securesms.util.LifecycleDisposable
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * UX for creating and changing a subscription
 */
class SubscribeFragment : DSLSettingsFragment(
  layoutId = R.layout.subscribe_fragment
) {

  private val lifecycleDisposable = LifecycleDisposable()

  /* private val supportTechSummary: CharSequence by lazy {
    SpannableStringBuilder(requireContext().getString(R.string.SubscribeFragment__support_technology_that_is_built_for_you_not))
      .append(" ")
      .append(
        SpanUtil.readMore(requireContext(), ContextCompat.getColor(requireContext(), R.color.signal_accent_primary)) {
          findNavController().navigate(SubscribeFragmentDirections.actionSubscribeFragmentToSubscribeLearnMoreBottomSheetDialog())
        }
      )
  } */

  private lateinit var processingDonationPaymentDialog: AlertDialog
  private lateinit var donationPaymentComponent: DonationPaymentComponent

  private val viewModel: SubscribeViewModel by viewModels(
    factoryProducer = {
      SubscribeViewModel.Factory(SubscriptionsRepository(ApplicationDependencies.getDonationsService()), donationPaymentComponent.donationPaymentRepository, FETCH_SUBSCRIPTION_TOKEN_REQUEST_CODE)
    }
  )

  override fun onResume() {
    super.onResume()
    viewModel.refreshActiveSubscription()
  }

  override fun bindAdapter(adapter: DSLSettingsAdapter) {
    donationPaymentComponent = findListener()!!
    viewModel.refresh()

    BadgePreview.register(adapter)
    CurrencySelection.register(adapter)
    Subscription.register(adapter)
    GooglePayButton.register(adapter)
    Progress.register(adapter)
    NetworkFailure.register(adapter)

    processingDonationPaymentDialog = MaterialAlertDialogBuilder(requireContext())
      .setView(R.layout.processing_payment_dialog)
      .setCancelable(false)
      .create()

    viewModel.state.observe(viewLifecycleOwner) { state ->
      adapter.submitList(getConfiguration(state).toMappingModelList())
    }

    lifecycleDisposable.bindTo(viewLifecycleOwner.lifecycle)
    lifecycleDisposable += viewModel.events.subscribe {
      when (it) {
        is DonationEvent.GooglePayUnavailableError -> Unit
        is DonationEvent.PaymentConfirmationError -> onPaymentError(it.throwable)
        is DonationEvent.PaymentConfirmationSuccess -> onPaymentConfirmed(it.badge)
        DonationEvent.RequestTokenError -> onPaymentError(null)
        DonationEvent.RequestTokenSuccess -> Log.w(TAG, "Successfully got request token from Google Pay")
        DonationEvent.SubscriptionCancelled -> onSubscriptionCancelled()
        is DonationEvent.SubscriptionCancellationFailed -> onSubscriptionFailedToCancel(it.throwable)
      }
    }

    lifecycleDisposable += donationPaymentComponent.googlePayResultPublisher.subscribe {
      viewModel.onActivityResult(it.requestCode, it.resultCode, it.data)
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    processingDonationPaymentDialog.hide()
  }

  private fun getConfiguration(state: SubscribeState): DSLConfiguration {
    if (state.hasInProgressSubscriptionTransaction || state.stage == SubscribeState.Stage.PAYMENT_PIPELINE) {
      processingDonationPaymentDialog.show()
    } else {
      processingDonationPaymentDialog.hide()
    }

    val areFieldsEnabled = state.stage == SubscribeState.Stage.READY && !state.hasInProgressSubscriptionTransaction

    return configure {
      customPref(BadgePreview.SubscriptionModel(state.selectedSubscription?.badge))

      sectionHeaderPref(
        title = DSLSettingsText.from(
          R.string.SubscribeFragment__signal_is_powered_by_people_like_you,
          DSLSettingsText.CenterModifier, DSLSettingsText.Title2BoldModifier
        )
      )

      /* noPadTextPref(
        title = DSLSettingsText.from(supportTechSummary, DSLSettingsText.CenterModifier)
      ) */

      space(DimensionUnit.DP.toPixels(16f).toInt())

      customPref(
        CurrencySelection.Model(
          selectedCurrency = state.currencySelection,
          isEnabled = areFieldsEnabled && state.activeSubscription?.isActive != true,
          onClick = {
            val selectableCurrencies = viewModel.getSelectableCurrencyCodes()
            if (selectableCurrencies != null) {
              findNavController().navigate(SubscribeFragmentDirections.actionSubscribeFragmentToSetDonationCurrencyFragment(false, selectableCurrencies.toTypedArray()))
            }
          }
        )
      )

      space(DimensionUnit.DP.toPixels(4f).toInt())

      @Suppress("CascadeIf")
      if (state.stage == SubscribeState.Stage.INIT) {
        customPref(
          Subscription.LoaderModel()
        )
      } else if (state.stage == SubscribeState.Stage.FAILURE) {
        space(DimensionUnit.DP.toPixels(69f).toInt())
        customPref(
          NetworkFailure.Model {
            viewModel.refresh()
          }
        )
        space(DimensionUnit.DP.toPixels(75f).toInt())
      } else {
        state.subscriptions.forEach {
          val isActive = state.activeSubscription?.activeSubscription?.level == it.level
          customPref(
            Subscription.Model(
              subscription = it,
              isSelected = state.selectedSubscription == it,
              isEnabled = areFieldsEnabled,
              isActive = isActive,
              willRenew = isActive && state.activeSubscription?.activeSubscription?.willCancelAtPeriodEnd() ?: false,
              onClick = { viewModel.setSelectedSubscription(it) },
              renewalTimestamp = TimeUnit.SECONDS.toMillis(state.activeSubscription?.activeSubscription?.endOfCurrentPeriod ?: 0L),
              selectedCurrency = state.currencySelection
            )
          )
        }
      }

      if (state.activeSubscription?.isActive == true) {
        space(DimensionUnit.DP.toPixels(16f).toInt())

        val activeAndSameLevel = state.activeSubscription.isActive &&
          state.selectedSubscription?.level == state.activeSubscription.activeSubscription?.level
        val isExpiring = state.activeSubscription.isActive && state.activeSubscription.activeSubscription?.willCancelAtPeriodEnd() == true

        primaryButton(
          text = DSLSettingsText.from(R.string.SubscribeFragment__update_subscription),
          isEnabled = areFieldsEnabled && (!activeAndSameLevel || isExpiring),
          onClick = {
            val price = viewModel.getPriceOfSelectedSubscription() ?: return@primaryButton
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.MONTH, 1)
            MaterialAlertDialogBuilder(requireContext())
              .setTitle(R.string.SubscribeFragment__update_subscription_question)
              .setMessage(
                getString(
                  R.string.SubscribeFragment__you_will_be_charged_the_full_amount_s_of,
                  FiatMoneyUtil.format(
                    requireContext().resources,
                    price,
                    FiatMoneyUtil.formatOptions().trimZerosAfterDecimal()
                  )
                )
              )
              .setPositiveButton(R.string.SubscribeFragment__update) { dialog, _ ->
                dialog.dismiss()
                viewModel.updateSubscription()
              }
              .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
              }
              .show()
          }
        )

        secondaryButtonNoOutline(
          text = DSLSettingsText.from(R.string.SubscribeFragment__cancel_subscription),
          isEnabled = areFieldsEnabled,
          onClick = {
            MaterialAlertDialogBuilder(requireContext())
              .setTitle(R.string.SubscribeFragment__confirm_cancellation)
              .setMessage(R.string.SubscribeFragment__you_wont_be_charged_again)
              .setPositiveButton(R.string.SubscribeFragment__confirm) { d, _ ->
                d.dismiss()
                viewModel.cancel()
              }
              .setNegativeButton(R.string.SubscribeFragment__not_now) { d, _ ->
                d.dismiss()
              }
              .show()
          }
        )
      } else {
        space(DimensionUnit.DP.toPixels(16f).toInt())

        customPref(
          GooglePayButton.Model(
            onClick = this@SubscribeFragment::onGooglePayButtonClicked,
            isEnabled = areFieldsEnabled && state.selectedSubscription != null
          )
        )

        secondaryButtonNoOutline(
          text = DSLSettingsText.from(R.string.SubscribeFragment__more_payment_options),
          icon = DSLSettingsIcon.from(R.drawable.ic_open_20, R.color.signal_accent_primary),
          onClick = {
            // TODO [alex] support page
          }
        )
      }
    }
  }

  private fun onGooglePayButtonClicked() {
    viewModel.requestTokenFromGooglePay()
  }

  private fun onPaymentConfirmed(badge: Badge) {
    findNavController().navigate(
      SubscribeFragmentDirections.actionSubscribeFragmentToSubscribeThanksForYourSupportBottomSheetDialog(badge).setIsBoost(false),
    )
  }

  private fun onPaymentError(throwable: Throwable?) {
    if (throwable is DonationExceptions.TimedOutWaitingForTokenRedemption) {
      Log.w(TAG, "Timeout occurred while redeeming token", throwable, true)
      MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.DonationsErrors__still_processing)
        .setMessage(R.string.DonationsErrors__your_payment)
        .setPositiveButton(android.R.string.ok) { dialog, _ ->
          dialog.dismiss()
          requireActivity().finish()
          requireActivity().startActivity(AppSettingsActivity.manageSubscriptions(requireContext()))
        }
        .show()
    } else if (throwable is DonationExceptions.SetupFailed) {
      Log.w(TAG, "Error occurred while processing payment", throwable, true)
      MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.DonationsErrors__payment_failed)
        .setMessage(R.string.DonationsErrors__your_payment)
        .setPositiveButton(android.R.string.ok) { dialog, _ ->
          dialog.dismiss()
        }
        .show()
    } else {
      Log.w(TAG, "Error occurred while trying to redeem token", throwable, true)
      MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.DonationsErrors__redemption_failed)
        .setMessage(R.string.DonationsErrors__please_contact_support)
        .setPositiveButton(R.string.Subscription__contact_support) { dialog, _ ->
          dialog.dismiss()
          requireActivity().finish()
          requireActivity().startActivity(AppSettingsActivity.help(requireContext(), HelpFragment.DONATION_INDEX))
        }
        .show()
    }
  }

  private fun onSubscriptionCancelled() {
    Snackbar.make(requireView(), R.string.SubscribeFragment__your_subscription_has_been_cancelled, Snackbar.LENGTH_LONG)
      .setTextColor(Color.WHITE)
      .show()

    requireActivity().finish()
    requireActivity().startActivity(AppSettingsActivity.home(requireContext()))
  }

  private fun onSubscriptionFailedToCancel(throwable: Throwable) {
    Log.w(TAG, "Failed to cancel subscription", throwable, true)
    MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.DonationsErrors__failed_to_cancel_subscription)
      .setMessage(R.string.DonationsErrors__subscription_cancellation_requires_an_internet_connection)
      .setPositiveButton(android.R.string.ok) { dialog, _ ->
        dialog.dismiss()
        findNavController().popBackStack()
      }
      .show()
  }

  companion object {
    private val TAG = Log.tag(SubscribeFragment::class.java)
    private const val FETCH_SUBSCRIPTION_TOKEN_REQUEST_CODE = 1000
  }
}