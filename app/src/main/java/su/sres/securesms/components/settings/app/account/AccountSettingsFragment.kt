package su.sres.securesms.components.settings.app.account

import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.Navigation
import su.sres.securesms.R
import su.sres.securesms.components.settings.DSLConfiguration
import su.sres.securesms.components.settings.DSLSettingsAdapter
import su.sres.securesms.components.settings.DSLSettingsFragment
import su.sres.securesms.components.settings.DSLSettingsText
import su.sres.securesms.components.settings.configure
import su.sres.securesms.recipients.Recipient
import su.sres.securesms.util.FeatureFlags

class AccountSettingsFragment : DSLSettingsFragment(R.string.AccountSettingsFragment__account) {

  lateinit var viewModel: AccountSettingsViewModel

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    // if (requestCode == CreateKbsPinActivity.REQUEST_NEW_PIN && resultCode == CreateKbsPinActivity.RESULT_OK) {
    //  Snackbar.make(requireView(), R.string.ConfirmKbsPinFragment__pin_created, Snackbar.LENGTH_LONG).setTextColor(Color.WHITE).show()
    // }
  }

  override fun onResume() {
    super.onResume()
    viewModel.refreshState()
  }

  override fun bindAdapter(adapter: DSLSettingsAdapter) {
    viewModel = ViewModelProvider(this)[AccountSettingsViewModel::class.java]

    viewModel.state.observe(viewLifecycleOwner) { state ->
      adapter.submitList(getConfiguration(state).toMappingModelList())
    }
  }

  private fun getConfiguration(state: AccountSettingsState): DSLConfiguration {
    return configure {

      // sectionHeaderPref(R.string.preferences_app_protection__signal_pin)

      /*  @Suppress("DEPRECATION")
      clickPref(
        title = DSLSettingsText.from(if (state.hasPin) R.string.preferences_app_protection__change_your_pin else R.string.preferences_app_protection__create_a_pin),
        onClick = {
          if (state.hasPin) {
            startActivityForResult(CreateKbsPinActivity.getIntentForPinChangeFromSettings(requireContext()), CreateKbsPinActivity.REQUEST_NEW_PIN)
          } else {
            startActivityForResult(CreateKbsPinActivity.getIntentForPinCreate(requireContext()), CreateKbsPinActivity.REQUEST_NEW_PIN)
          }
        }
      )

      switchPref(
        title = DSLSettingsText.from(R.string.preferences_app_protection__pin_reminders),
        summary = DSLSettingsText.from(R.string.AccountSettingsFragment__youll_be_asked_less_frequently),
        isChecked = state.hasPin && state.pinRemindersEnabled,
        isEnabled = state.hasPin,
        onClick = {
          setPinRemindersEnabled(!state.pinRemindersEnabled)
        }
      )

      switchPref(
        title = DSLSettingsText.from(R.string.preferences_app_protection__registration_lock),
        summary = DSLSettingsText.from(R.string.AccountSettingsFragment__require_your_signal_pin),
        isChecked = state.registrationLockEnabled,
        isEnabled = state.hasPin,
        onClick = {
          setRegistrationLockEnabled(!state.registrationLockEnabled)
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__advanced_pin_settings),
        onClick = {
          Navigation.findNavController(requireView()).navigate(R.id.action_accountSettingsFragment_to_advancedPinSettingsActivity)
        }
      )

      dividerPref() */

      sectionHeaderPref(R.string.AccountSettingsFragment__account)

      if (FeatureFlags.changeUserLogin() && Recipient.self().changeLoginCapability == Recipient.Capability.SUPPORTED) {
        clickPref(
          title = DSLSettingsText.from(R.string.AccountSettingsFragment__change_phone_number),
          onClick = {
            Navigation.findNavController(requireView()).navigate(R.id.action_accountSettingsFragment_to_changeUserLoginFragment)
          }
        )
      }

      /* clickPref(
        title = DSLSettingsText.from(R.string.preferences_chats__transfer_account),
        summary = DSLSettingsText.from(R.string.preferences_chats__transfer_account_to_a_new_android_device),
        onClick = {
          Navigation.findNavController(requireView()).navigate(R.id.action_accountSettingsFragment_to_oldDeviceTransferActivity)
        }
      ) */

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__delete_account, ContextCompat.getColor(requireContext(), R.color.signal_alert_primary)),
        onClick = {
          Navigation.findNavController(requireView()).navigate(R.id.action_accountSettingsFragment_to_deleteAccountFragment)
        }
      )
    }
  }

  /* private fun setRegistrationLockEnabled(enabled: Boolean) {
    if (enabled) {
      RegistrationLockV2Dialog.showEnableDialog(requireContext()) { viewModel.refreshState() }
    } else {
      RegistrationLockV2Dialog.showDisableDialog(requireContext()) { viewModel.refreshState() }
    }
  }

  private fun setPinRemindersEnabled(enabled: Boolean) {
    if (!enabled) {
      val context: Context = requireContext()
      val metrics: DisplayMetrics = resources.displayMetrics

      val dialog: AlertDialog = AlertDialog.Builder(context, if (ThemeUtil.isDarkTheme(context)) R.style.Theme_Signal_AlertDialog_Dark_Cornered_ColoredAccent else R.style.Theme_Signal_AlertDialog_Light_Cornered_ColoredAccent)
        .create()

      dialog.show()
      dialog.window!!.setLayout((metrics.widthPixels * .80).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)

      val pinEditText = DialogCompat.requireViewById(dialog, R.id.reminder_disable_pin) as EditText
      val statusText = DialogCompat.requireViewById(dialog, R.id.reminder_disable_status) as TextView
      val cancelButton = DialogCompat.requireViewById(dialog, R.id.reminder_disable_cancel)
      val turnOffButton = DialogCompat.requireViewById(dialog, R.id.reminder_disable_turn_off)

      pinEditText.post {
        if (pinEditText.requestFocus()) {
          ServiceUtil.getInputMethodManager(pinEditText.context).showSoftInput(pinEditText, 0)
        }
      }

      ViewCompat.setAutofillHints(pinEditText, HintConstants.AUTOFILL_HINT_PASSWORD)

      when (SignalStore.pinValues().keyboardType) {
        PinKeyboardType.NUMERIC -> pinEditText.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        PinKeyboardType.ALPHA_NUMERIC -> pinEditText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
      }

      pinEditText.addTextChangedListener(object : SimpleTextWatcher() {
        override fun onTextChanged(text: String) {
          turnOffButton.isEnabled = text.length >= KbsConstants.MINIMUM_PIN_LENGTH
        }
      })

      pinEditText.typeface = Typeface.DEFAULT
      turnOffButton.setOnClickListener {
        val pin = pinEditText.text.toString()
        val correct = PinHashing.verifyLocalPinHash(SignalStore.kbsValues().localPinHash!!, pin)
        if (correct) {
          SignalStore.pinValues().setPinRemindersEnabled(false)
          viewModel.refreshState()
          dialog.dismiss()
        } else {
          statusText.setText(R.string.preferences_app_protection__incorrect_pin_try_again)
        }
      }

      cancelButton.setOnClickListener { dialog.dismiss() }
    } else {
      SignalStore.pinValues().setPinRemindersEnabled(true)
      viewModel.refreshState()
    }
  } */
}