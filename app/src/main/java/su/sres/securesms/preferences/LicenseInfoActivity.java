/*
 * Copyright (C) 2022 Anton Alipov, sole trader
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

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;

import su.sres.securesms.BaseActivity;
import su.sres.securesms.activation.License;
import su.sres.securesms.R;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.RecipientDatabase;
import su.sres.securesms.keyvalue.ServiceConfigurationValues;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.securesms.util.DynamicTheme;

public class LicenseInfoActivity extends BaseActivity {

    private final DynamicTheme dynamicTheme = new DynamicTheme();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dynamicTheme.onCreate(this);

        setContentView(R.layout.license_info_activity);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        TextView status = findViewById(R.id.activationStatus);

        ServiceConfigurationValues config = SignalStore.serviceConfigurationValues();

        byte[] licenseBytes = config.retrieveLicense();

        if (licenseBytes == null) {
            status.setText(R.string.LicenseInfoActivity_status_nokey);
        } else {

            try {
                License license = License.Create.from(licenseBytes);

                if (license.isExpired()) {
                    status.setText(R.string.LicenseInfoActivity_status_expired);
                } else if (license.getFeatures().get("Valid From").getLong() > System.currentTimeMillis()) {
                    status.setText(R.string.LicenseInfoActivity_status_nyv);
                } else if (config.isLicensed()) {
                    RecipientDatabase rdb = DatabaseFactory.getRecipientDatabase(this);
                    int actualUsers = rdb.getRegistered().size();

                    String[] volumes = license.getFeatures().get("Volumes").getString().split(":");
                    int licensedUsers = Integer.valueOf(volumes[1]);

                    if (licensedUsers < actualUsers) {
                        status.setText(R.string.LicenseInfoActivity_status_oversubscribed);
                    } else {
                        status.setText(R.string.LicenseInfoActivity_status_active);
                    }

                } else {
                    // there's the license file with seemingly good parameters, but somehow we're not licensed still.
                    status.setText(R.string.LicenseInfoActivity_status_invalid);
                }

            } catch (Exception e) {
                status.setText(R.string.LicenseInfoActivity_status_uncertain);
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