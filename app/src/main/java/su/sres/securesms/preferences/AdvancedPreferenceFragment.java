package su.sres.securesms.preferences;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.ContactsContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;

import su.sres.securesms.BuildConfig;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.securesms.logging.Log;
import android.widget.Toast;

import com.google.firebase.iid.FirebaseInstanceId;

import su.sres.securesms.ApplicationPreferencesActivity;
import su.sres.securesms.R;
import su.sres.securesms.registration.RegistrationNavigationActivity;
import su.sres.securesms.contacts.ContactAccessor;
import su.sres.securesms.contacts.ContactIdentityManager;
import su.sres.securesms.logsubmit.SubmitDebugLogActivity;
import su.sres.securesms.util.FeatureFlags;
import su.sres.securesms.util.TextSecurePreferences;
import su.sres.securesms.util.task.ProgressDialogAsyncTask;
import org.whispersystems.libsignal.util.guava.Optional;
import su.sres.signalservice.api.SignalServiceAccountManager;
import su.sres.signalservice.api.push.exceptions.AuthorizationFailedException;

import java.io.IOException;

public class AdvancedPreferenceFragment extends CorrectedPreferenceFragment {
  private static final String TAG = AdvancedPreferenceFragment.class.getSimpleName();

  private static final String INTERNAL_PREF         = "pref_internal";
  private static final String LICENSE_INFO          = "pref_license_info";
  private static final String PUSH_MESSAGING_PREF   = "pref_toggle_push_messaging";
  private static final String SUBMIT_DEBUG_LOG_PREF = "pref_submit_debug_logs";

  private static final int PICK_IDENTITY_CONTACT = 1;

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);

    initializeIdentitySelection();

    Preference submitDebugLog = this.findPreference(SUBMIT_DEBUG_LOG_PREF);
    submitDebugLog.setOnPreferenceClickListener(new SubmitDebugLogListener());
    submitDebugLog.setSummary(getVersion(getActivity()));

    Preference internalPreference = this.findPreference(INTERNAL_PREF);
    internalPreference.setVisible(FeatureFlags.internalUser());
    internalPreference.setOnPreferenceClickListener(preference -> {
      if (FeatureFlags.internalUser()) {
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(R.anim.slide_from_end, R.anim.slide_to_start, R.anim.slide_from_start, R.anim.slide_to_end)
                .replace(android.R.id.content, new InternalOptionsPreferenceFragment())
                .addToBackStack(null)
                .commit();
        return true;
      } else {
        return false;
      }
    });

    Preference licenseInfo = this.findPreference(LICENSE_INFO);
    licenseInfo.setOnPreferenceClickListener(new LicenseInfoListener());
    licenseInfo.setSummary(R.string.LicenseInfoActivity_summary);
  }

  @Override
  public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
    addPreferencesFromResource(R.xml.preferences_advanced);
  }

  @Override
  public void onResume() {
    super.onResume();
    ((ApplicationPreferencesActivity) getActivity()).getSupportActionBar().setTitle(R.string.preferences__advanced);

    initializePushMessagingToggle();
  }

  @Override
  public void onActivityResult(int reqCode, int resultCode, Intent data) {
    super.onActivityResult(reqCode, resultCode, data);

    Log.i(TAG, "Got result: " + resultCode + " for req: " + reqCode);
    if (resultCode == Activity.RESULT_OK && reqCode == PICK_IDENTITY_CONTACT) {
      handleIdentitySelection(data);
    }
  }

  private void initializePushMessagingToggle() {
    CheckBoxPreference preference = (CheckBoxPreference)this.findPreference(PUSH_MESSAGING_PREF);

    if (TextSecurePreferences.isPushRegistered(getActivity())) {
      preference.setChecked(true);
      preference.setSummary(TextSecurePreferences.getLocalNumber(getActivity()));
    } else {
      preference.setChecked(false);
      preference.setSummary(R.string.preferences__free_private_messages_and_calls);
    }

    preference.setOnPreferenceChangeListener(new PushMessagingClickListener());
  }

  private void initializeIdentitySelection() {
    ContactIdentityManager identity = ContactIdentityManager.getInstance(getActivity());

    Preference preference = this.findPreference(TextSecurePreferences.IDENTITY_PREF);

    if (identity.isSelfIdentityAutoDetected()) {
      this.getPreferenceScreen().removePreference(preference);
    } else {
      Uri contactUri = identity.getSelfIdentityUri();

      if (contactUri != null) {
        String contactName = ContactAccessor.getInstance().getNameFromContact(getActivity(), contactUri);
        preference.setSummary(String.format(getString(R.string.ApplicationPreferencesActivity_currently_s),
                                            contactName));
      }

      preference.setOnPreferenceClickListener(new IdentityPreferenceClickListener());
    }
  }

  private @NonNull String getVersion(@Nullable Context context) {
    if (context == null) return "";

    String app     = context.getString(R.string.app_name);
    String version = BuildConfig.VERSION_NAME;

    return String.format("%s %s", app, version);
  }

  private class IdentityPreferenceClickListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      Intent intent = new Intent(Intent.ACTION_PICK);
      intent.setType(ContactsContract.Contacts.CONTENT_TYPE);
      startActivityForResult(intent, PICK_IDENTITY_CONTACT);
      return true;
    }
  }

  private void handleIdentitySelection(Intent data) {
    Uri contactUri = data.getData();

    if (contactUri != null) {
      TextSecurePreferences.setIdentityContactUri(getActivity(), contactUri.toString());
      initializeIdentitySelection();
    }
  }

  private class SubmitDebugLogListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      final Intent intent = new Intent(getActivity(), SubmitDebugLogActivity.class);
      startActivity(intent);
      return true;
    }
  }

  private class LicenseInfoListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      final Intent intent = new Intent(getActivity(), LicenseInfoActivity.class);
      startActivity(intent);
      return true;
    }
  }

  private class PushMessagingClickListener implements Preference.OnPreferenceChangeListener {
    private static final int SUCCESS       = 0;
    private static final int NETWORK_ERROR = 1;

    private class DisablePushMessagesTask extends ProgressDialogAsyncTask<Void, Void, Integer> {
      private final CheckBoxPreference checkBoxPreference;

      public DisablePushMessagesTask(final CheckBoxPreference checkBoxPreference) {
        super(getActivity(), R.string.ApplicationPreferencesActivity_unregistering, R.string.ApplicationPreferencesActivity_unregistering_from_signal_messages_and_calls);
        this.checkBoxPreference = checkBoxPreference;
      }

      @Override
      protected void onPostExecute(Integer result) {
        super.onPostExecute(result);
        switch (result) {
        case NETWORK_ERROR:
          Toast.makeText(getActivity(),
                         R.string.ApplicationPreferencesActivity_error_connecting_to_server,
                         Toast.LENGTH_LONG).show();
          break;
        case SUCCESS:
          TextSecurePreferences.setPushRegistered(getActivity(), false);
          SignalStore.registrationValues().clearRegistrationComplete();
          initializePushMessagingToggle();
          break;
        }
      }

      @Override
      protected Integer doInBackground(Void... params) {
        try {
          Context                     context        = getActivity();
          SignalServiceAccountManager accountManager = ApplicationDependencies.getSignalServiceAccountManager();

          try {
            accountManager.setGcmId(Optional.<String>absent());
          } catch (AuthorizationFailedException e) {
            Log.w(TAG, e);
          }

          if (!TextSecurePreferences.isFcmDisabled(context)) {
            FirebaseInstanceId.getInstance().deleteInstanceId();
          }

          return SUCCESS;
        } catch (IOException ioe) {
          Log.w(TAG, ioe);
          return NETWORK_ERROR;
        }
      }
    }

    @Override
    public boolean onPreferenceChange(final Preference preference, Object newValue) {
      if (((CheckBoxPreference)preference).isChecked()) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setIconAttribute(R.attr.dialog_info_icon);
        builder.setTitle(R.string.ApplicationPreferencesActivity_disable_signal_messages_and_calls);
        builder.setMessage(R.string.ApplicationPreferencesActivity_disable_signal_messages_and_calls_by_unregistering);
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            new DisablePushMessagesTask((CheckBoxPreference)preference).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
          }
        });
        builder.show();
      } else {
        startActivity(RegistrationNavigationActivity.newIntentForReRegistration(requireContext()));
      }

      return false;
    }
  }
}
