package su.sres.securesms.preferences;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.Preference;

import org.greenrobot.eventbus.EventBus;

import su.sres.securesms.ApplicationPreferencesActivity;
import su.sres.securesms.R;
import su.sres.core.util.logging.Log;
import su.sres.securesms.devicetransfer.olddevice.OldDeviceTransferActivity;
import su.sres.securesms.permissions.Permissions;
import su.sres.securesms.util.TextSecurePreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ChatsPreferenceFragment extends ListSummaryPreferenceFragment {

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);

    findPreference(TextSecurePreferences.MESSAGE_BODY_TEXT_SIZE_PREF)
        .setOnPreferenceChangeListener(new ListSummaryListener());

    findPreference(TextSecurePreferences.BACKUP).setOnPreferenceClickListener(unused -> {
      goToBackupsPreferenceFragment();
      return true;
    });

    findPreference(TextSecurePreferences.TRANSFER).setOnPreferenceClickListener(unused -> {
      goToTransferAccount();
      return true;
    });

    initializeListSummary((ListPreference) findPreference(TextSecurePreferences.MESSAGE_BODY_TEXT_SIZE_PREF));
  }

  @Override
  public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
    addPreferencesFromResource(R.xml.preferences_chats);
  }

  @Override
  public void onResume() {
    super.onResume();
    ((ApplicationPreferencesActivity)getActivity()).getSupportActionBar().setTitle(R.string.preferences_chats__chats);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    EventBus.getDefault().unregister(this);
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

  private void goToBackupsPreferenceFragment() {
    ((ApplicationPreferencesActivity) requireActivity()).pushFragment(new BackupsPreferenceFragment());
  }

  private void goToTransferAccount() {
    requireContext().startActivity(new Intent(requireContext(), OldDeviceTransferActivity.class));
  }

  public static CharSequence getSummary(Context context) {
    return null;
  }
}
