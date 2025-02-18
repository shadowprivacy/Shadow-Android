package su.sres.securesms.components.settings.app.privacy.advanced

import android.app.ProgressDialog
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.text.SpannableStringBuilder
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import su.sres.securesms.R
import su.sres.securesms.components.settings.DSLConfiguration
import su.sres.securesms.components.settings.DSLSettingsAdapter
import su.sres.securesms.components.settings.DSLSettingsFragment
import su.sres.securesms.components.settings.DSLSettingsText
import su.sres.securesms.components.settings.configure
import su.sres.securesms.keyvalue.SignalStore
import su.sres.securesms.registration.RegistrationNavigationActivity
import su.sres.securesms.util.CommunicationActions
import su.sres.securesms.util.SpanUtil
import su.sres.securesms.util.ViewUtil

class AdvancedPrivacySettingsFragment : DSLSettingsFragment(R.string.preferences__advanced) {

  lateinit var viewModel: AdvancedPrivacySettingsViewModel

  private val sealedSenderSummary: CharSequence by lazy {
    SpanUtil.learnMore(
      requireContext(),
      ContextCompat.getColor(requireContext(), R.color.signal_text_primary)
    ) {
      CommunicationActions.openBrowserLink(
        requireContext(),
        getString(R.string.AdvancedPrivacySettingsFragment__sealed_sender_link)
      )
    }
  }

  var progressDialog: ProgressDialog? = null

  val statusIcon: CharSequence by lazy {
    val unidentifiedDeliveryIcon = requireNotNull(
      ContextCompat.getDrawable(
        requireContext(),
        R.drawable.ic_unidentified_delivery
      )
    )
    unidentifiedDeliveryIcon.setBounds(0, 0, ViewUtil.dpToPx(20), ViewUtil.dpToPx(20))
    val iconTint = ContextCompat.getColor(requireContext(), R.color.signal_text_primary_dialog)
    unidentifiedDeliveryIcon.colorFilter = PorterDuffColorFilter(iconTint, PorterDuff.Mode.SRC_IN)

    SpanUtil.buildImageSpan(unidentifiedDeliveryIcon)
  }

  override fun onResume() {
    super.onResume()
    viewModel.refresh()
  }

  override fun bindAdapter(adapter: DSLSettingsAdapter) {
    val repository = AdvancedPrivacySettingsRepository(requireContext())
    val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
    val factory = AdvancedPrivacySettingsViewModel.Factory(preferences, repository)

    viewModel = ViewModelProvider(this, factory)[AdvancedPrivacySettingsViewModel::class.java]

    viewModel.state.observe(viewLifecycleOwner) {
      if (it.showProgressSpinner) {
        if (progressDialog?.isShowing == false) {
          progressDialog = ProgressDialog.show(requireContext(), null, null, true)
        }
      } else {
        progressDialog?.hide()
      }

      adapter.submitList(getConfiguration(it).toMappingModelList())
    }

    viewModel.events.observe(viewLifecycleOwner) {
      if (it == AdvancedPrivacySettingsViewModel.Event.DISABLE_PUSH_FAILED) {
        Toast.makeText(
          requireContext(),
          R.string.ApplicationPreferencesActivity_error_connecting_to_server,
          Toast.LENGTH_LONG
        ).show()
      }
    }
  }

  private fun getConfiguration(state: AdvancedPrivacySettingsState): DSLConfiguration {
    return configure {

      switchPref(
        title = DSLSettingsText.from(R.string.preferences__signal_messages_and_calls),
        summary = DSLSettingsText.from(getPushToggleSummary(state.isPushEnabled)),
        isChecked = state.isPushEnabled
      ) {
        if (state.isPushEnabled) {
          val builder = MaterialAlertDialogBuilder(requireContext()).apply {
            setMessage(R.string.ApplicationPreferencesActivity_disable_signal_messages_and_calls_by_unregistering)
            setNegativeButton(android.R.string.cancel, null)
            setPositiveButton(
              android.R.string.ok
            ) { _, _ -> viewModel.disablePushMessages() }
          }

          val icon: Drawable = requireNotNull(ContextCompat.getDrawable(builder.context, R.drawable.ic_info_outline))
          icon.setBounds(0, 0, ViewUtil.dpToPx(32), ViewUtil.dpToPx(32))

          val title = TextView(builder.context)
          val padding = ViewUtil.dpToPx(16)
          title.setText(R.string.ApplicationPreferencesActivity_disable_signal_messages_and_calls)
          title.setPadding(padding, padding, padding, padding)
          title.compoundDrawablePadding = padding / 2
          TextViewCompat.setTextAppearance(title, R.style.TextAppearance_Signal_Title2_MaterialDialog)
          TextViewCompat.setCompoundDrawablesRelative(title, icon, null, null, null)

          builder
            .setCustomTitle(title)
            .show()
        } else {
          startActivity(RegistrationNavigationActivity.newIntentForReRegistration(requireContext()))
        }
      }

      switchPref(
        title = DSLSettingsText.from(R.string.preferences_advanced__always_relay_calls),
        summary = DSLSettingsText.from(R.string.preferences_advanced__relay_all_calls_through_the_signal_server_to_avoid_revealing_your_ip_address),
        isChecked = state.alwaysRelayCalls
      ) {
        viewModel.setAlwaysRelayCalls(!state.alwaysRelayCalls)
      }

      dividerPref()

      sectionHeaderPref(R.string.preferences_communication__category_sealed_sender)

      switchPref(
        title = DSLSettingsText.from(
          SpannableStringBuilder(getString(R.string.AdvancedPrivacySettingsFragment__show_status_icon))
            .append(" ")
            .append(statusIcon)
        ),
        summary = DSLSettingsText.from(R.string.AdvancedPrivacySettingsFragment__show_an_icon),
        isChecked = state.showSealedSenderStatusIcon
      ) {
        viewModel.setShowStatusIconForSealedSender(!state.showSealedSenderStatusIcon)
      }

      textPref(
        summary = DSLSettingsText.from(sealedSenderSummary)
      )
    }
  }

  private fun getPushToggleSummary(isPushEnabled: Boolean): String {
    return if (isPushEnabled) {
      SignalStore.account().userLogin!!
    } else {
      getString(R.string.preferences__free_private_messages_and_calls)
    }
  }
}