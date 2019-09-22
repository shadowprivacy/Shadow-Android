/**
 * Copyright (C) 2014 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package su.sres.securesms.preferences;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import androidx.annotation.Nullable;

import su.sres.securesms.PassphraseRequiredActionBarActivity;
import su.sres.securesms.R;
import su.sres.securesms.components.CustomDefaultPreference;
import su.sres.securesms.database.ApnDatabase;
import su.sres.securesms.logging.Log;
import su.sres.securesms.mms.LegacyMmsConnection;
import su.sres.securesms.util.TelephonyUtil;
import su.sres.securesms.util.TextSecurePreferences;

import java.io.IOException;


public class MmsPreferencesFragment extends CorrectedPreferenceFragment {

  private static final String TAG = MmsPreferencesFragment.class.getSimpleName();

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);

    ((PassphraseRequiredActionBarActivity) getActivity()).getSupportActionBar()
        .setTitle(R.string.preferences__advanced_mms_access_point_names);
  }

  @Override
  public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
    addPreferencesFromResource(R.xml.preferences_manual_mms);
  }

  @Override
  public void onResume() {
    super.onResume();
    new LoadApnDefaultsTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  private class LoadApnDefaultsTask extends AsyncTask<Void, Void, LegacyMmsConnection.Apn> {

    @Override
    protected LegacyMmsConnection.Apn doInBackground(Void... params) {
      try {
        Context context = getActivity();

        if (context != null) {
          return ApnDatabase.getInstance(context)
                            .getDefaultApnParameters(TelephonyUtil.getMccMnc(context),
                                                     TelephonyUtil.getApn(context));
        }
      } catch (IOException e) {
        Log.w(TAG, e);
      }

      return null;
    }

    @Override
    protected void onPostExecute(LegacyMmsConnection.Apn apnDefaults) {
      ((CustomDefaultPreference)findPreference(TextSecurePreferences.MMSC_HOST_PREF))
          .setValidator(new CustomDefaultPreference.CustomDefaultPreferenceDialogFragmentCompat.UriValidator())
          .setDefaultValue(apnDefaults.getMmsc());

      ((CustomDefaultPreference)findPreference(TextSecurePreferences.MMSC_PROXY_HOST_PREF))
          .setValidator(new CustomDefaultPreference.CustomDefaultPreferenceDialogFragmentCompat.HostnameValidator())
          .setDefaultValue(apnDefaults.getProxy());

      ((CustomDefaultPreference)findPreference(TextSecurePreferences.MMSC_PROXY_PORT_PREF))
          .setValidator(new CustomDefaultPreference.CustomDefaultPreferenceDialogFragmentCompat.PortValidator())
          .setDefaultValue(apnDefaults.getPort());

      ((CustomDefaultPreference)findPreference(TextSecurePreferences.MMSC_USERNAME_PREF))
          .setDefaultValue(apnDefaults.getPort());

      ((CustomDefaultPreference)findPreference(TextSecurePreferences.MMSC_PASSWORD_PREF))
          .setDefaultValue(apnDefaults.getPassword());

      ((CustomDefaultPreference)findPreference(TextSecurePreferences.MMS_USER_AGENT))
          .setDefaultValue(LegacyMmsConnection.USER_AGENT);
    }
  }

}
