package su.sres.securesms.registration;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import su.sres.securesms.R;
import su.sres.core.util.logging.Log;

public final class RegistrationNavigationActivity extends AppCompatActivity {

    private static final String TAG = Log.tag(RegistrationNavigationActivity.class);

    public static final String RE_REGISTRATION_EXTRA = "re_registration";

    public static Intent newIntentForNewRegistration(@NonNull Context context) {
        Intent intent = new Intent(context, RegistrationNavigationActivity.class);
        intent.putExtra(RE_REGISTRATION_EXTRA, false);
        return intent;
    }

    public static Intent newIntentForReRegistration(@NonNull Context context) {
        Intent intent = new Intent(context, RegistrationNavigationActivity.class);
        intent.putExtra(RE_REGISTRATION_EXTRA, true);
        return intent;
    }

    @Override
    protected void attachBaseContext(@NonNull Context newBase) {
        getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.attachBaseContext(newBase);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registration_navigation);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}