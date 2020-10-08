/*
 * Copyright (C) 2020 Anton Alipov, sole trader
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

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.provider.Settings;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.util.concurrent.TimeUnit;

import su.sres.securesms.activation.License;
import su.sres.securesms.BaseActionBarActivity;
import su.sres.securesms.R;
import su.sres.securesms.jobs.LicenseManagementJob;
import su.sres.securesms.jobs.LicenseManagementJob.NullPsidException;
import su.sres.securesms.keyvalue.ServiceConfigurationValues;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.securesms.util.DynamicTheme;
import su.sres.securesms.util.ExpirationUtil;

public class LicenseInfoActivity extends BaseActionBarActivity {

    private static final String TAG = LicenseInfoActivity.class.getSimpleName();

    private final DynamicTheme dynamicTheme = new DynamicTheme();

    @SuppressLint("HardwareIds")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dynamicTheme.onCreate(this);

        setContentView(R.layout.license_info_activity);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        TextView status     = findViewById(R.id.activationStatus),
                 validFrom  = findViewById(R.id.validFrom),
                 validUntil = findViewById(R.id.validUntil),
                 serial     = findViewById(R.id.serialNumber),
                 platformId = findViewById(R.id.psid);

        String psid = "null";
        String na = getString(R.string.LicenseInfoActivity_na);

        ServiceConfigurationValues config = SignalStore.serviceConfigurationValues();

        try {
            psid = LicenseManagementJob.calculatePsid(Settings.Secure.getString(this.getContentResolver(), Settings.Secure.ANDROID_ID));
        } catch (NullPsidException | NoSuchAlgorithmException e) {
            // noop
        }

        platformId.setText(String.format(getString(R.string.LicenseInfoActivity_psid), psid));

        byte [] licenseBytes = config.retrieveLicense();

        if (licenseBytes == null) {

            if (config.getTrialStatus() == 1) {
                int secondsLeft = Long.valueOf(TimeUnit.MILLISECONDS.toSeconds(config.getTrialStartTime() + TimeUnit.DAYS.toMillis(config.getTrialDuration()) - System.currentTimeMillis())).intValue();

                if (secondsLeft > 0) {
                    status.setText(String.format(getString(R.string.LicenseInfoActivity_status_trial_active), ExpirationUtil.getExpirationAbbreviatedDisplayValue(this, secondsLeft)));
                } else {
                    status.setText(getString(R.string.LicenseInfoActivity_status_trial_expired));
                }

                validFrom.setVisibility(View.INVISIBLE);
                validUntil.setVisibility(View.INVISIBLE);
                serial.setVisibility(View.INVISIBLE);
            } else {
                status.setText(R.string.LicenseInfoActivity_status_nokey);
                validFrom.setText(String.format(getString(R.string.LicenseInfoActivity_valid_from), na));
                validUntil.setText(String.format(getString(R.string.LicenseInfoActivity_valid_thru), na));
                serial.setText(String.format(getString(R.string.LicenseInfoActivity_serial), na));
            }

        } else {

            try {
                License license = License.Create.from(licenseBytes);

                validFrom.setText(String.format(getString(R.string.LicenseInfoActivity_valid_from), new Timestamp(license.getFeatures().get("Valid From").getLong()).toString()));
                validUntil.setText(String.format(getString(R.string.LicenseInfoActivity_valid_thru), new Timestamp(license.getFeatures().get("expiryDate").getDate().getTime()).toString()));
                serial.setText(String.format(getString(R.string.LicenseInfoActivity_serial), license.getLicenseId()));

                if(license.isExpired()) {
                    status.setText(R.string.LicenseInfoActivity_status_expired);
                } else if (license.getFeatures().get("Valid From").getLong() > System.currentTimeMillis()) {
                    status.setText(R.string.LicenseInfoActivity_status_nyv);
                } else if (config.isLicensed()) {
                    status.setText(R.string.LicenseInfoActivity_status_active);
                } else {
                    // there's the license file with seemingly good parameters, but we're not licensed still. Must be due to web-validation failure (license revoked etc)
                    status.setText(R.string.LicenseInfoActivity_status_invalid);
                }

            } catch (Exception e) {
                status.setText(R.string.LicenseInfoActivity_status_uncertain);
                validFrom.setText(String.format(getString(R.string.LicenseInfoActivity_valid_from), na));
                validUntil.setText(String.format(getString(R.string.LicenseInfoActivity_valid_thru), na));
                serial.setText(String.format(getString(R.string.LicenseInfoActivity_serial), na));
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        dynamicTheme.onResume(this);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}

