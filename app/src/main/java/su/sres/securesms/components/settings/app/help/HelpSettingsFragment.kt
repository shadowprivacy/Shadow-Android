package su.sres.securesms.components.settings.app.help

import android.content.DialogInterface
import android.content.Intent
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.navigation.Navigation
import su.sres.securesms.BuildConfig
import su.sres.securesms.R
import su.sres.securesms.components.settings.DSLConfiguration
import su.sres.securesms.components.settings.DSLSettingsAdapter
import su.sres.securesms.components.settings.DSLSettingsFragment
import su.sres.securesms.components.settings.DSLSettingsText
import su.sres.securesms.components.settings.configure
import su.sres.securesms.jobs.CertificatePullJob
import su.sres.securesms.preferences.LicenseInfoActivity

class HelpSettingsFragment : DSLSettingsFragment(R.string.preferences__help) {

  override fun bindAdapter(adapter: DSLSettingsAdapter) {
    adapter.submitList(getConfiguration().toMappingModelList())
  }

  fun getConfiguration(): DSLConfiguration {
    return configure {

      clickPref(
        title = DSLSettingsText.from(R.string.HelpSettingsFragment__contact_us),
        onClick = {
          Navigation.findNavController(requireView()).navigate(R.id.action_helpSettingsFragment_to_helpFragment)
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.HelpSettingsFragment__debug_log),
        onClick = {
          Navigation.findNavController(requireView()).navigate(R.id.action_helpSettingsFragment_to_submitDebugLogActivity)
        }
      )

      dividerPref()

      textPref(
        title = DSLSettingsText.from(R.string.HelpSettingsFragment__version),
        summary = DSLSettingsText.from(BuildConfig.VERSION_NAME)
      )

      clickPref(
        title = DSLSettingsText.from(R.string.LicenseInfoActivity_title),
        summary = DSLSettingsText.from(R.string.LicenseInfoActivity_summary),
        onClick = {
          val intent = Intent(activity, LicenseInfoActivity::class.java)
          startActivity(intent)
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.CertificatePull_alert_builder_title),
        summary = DSLSettingsText.from(R.string.CertificatePull_caution),
        onClick = {
          val alertDialogBuilder = AlertDialog.Builder(requireActivity())

          alertDialogBuilder.setTitle(R.string.CertificatePull_alert_builder_title)
          alertDialogBuilder.setMessage(R.string.CertificatePull_alert_builder_warning)
          alertDialogBuilder.setPositiveButton(R.string.CertificatePull_proceed, DialogInterface.OnClickListener { dialog, id -> CertificatePullJob.scheduleIfNecessary() })
          alertDialogBuilder.setNeutralButton(R.string.CertificatePull_cancel, DialogInterface.OnClickListener { dialog, id -> dialog.dismiss() })
          alertDialogBuilder.setCancelable(true)
          alertDialogBuilder.show()
        }
      )

      textPref(
        summary = DSLSettingsText.from(
          StringBuilder().apply {
            append(getString(R.string.HelpFragment__copyright_signal_messenger))
            append("\n")
            append(getString(R.string.HelpFragment__licenced_under_the_gplv3))
          }
        )
      )
    }
  }
}