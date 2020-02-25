package su.sres.securesms;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

import org.apache.commons.validator.routines.UrlValidator;

import org.greenrobot.eventbus.EventBus;

import java.security.SecureRandom;

import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.events.ServerSetEvent;
import su.sres.securesms.logging.Log;
import su.sres.securesms.components.LabeledEditText;
import su.sres.securesms.util.TextSecurePreferences;

public class InitialActivity extends AppCompatActivity implements OnClickListener {

    private static final String TAG = InitialActivity.class.getSimpleName();

    private LabeledEditText shadowAddress;
    private LabeledEditText shadowPort;
    private Button buttonSocket;

    private String shadowUrl;

    private String[] schemes = {"https"};
    private UrlValidator validator = new UrlValidator(schemes, 4L);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate()");
        if (((ApplicationContext) getApplication()).getServerSet()) {
            Log.i(TAG, "the server URL is already set up, quitting the activity");
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }

        else {
            Log.i(TAG, "the server URL is a default one, proceeding to set the real value");
            setContentView(R.layout.initial_activity);

            shadowAddress = findViewById(R.id.shadowAddress);
            shadowPort = findViewById(R.id.shadowPort);
            buttonSocket = findViewById(R.id.buttonSocket);

            buttonSocket.setOnClickListener(this);
        }
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy()");
        super.onDestroy();

    }

    @Override
    public void onClick(View v) {
        if (TextUtils.isEmpty(shadowAddress.getText()))  {
            Toast.makeText(this, getString(R.string.InitialActivity_you_must_specify_the_server_address_and_port), Toast.LENGTH_LONG).show();
            return;
        }

        if (TextUtils.isEmpty(shadowPort.getText()))  {
            Log.w(TAG, "no server port specified, using the default 8080");
            shadowPort.setText("8080");
        }

        String candidateURL = "https://" + shadowAddress.getText().toString() + ":" + shadowPort.getText().toString();

        if (!validator.isValid(candidateURL)) {
            Toast.makeText(this, getString(R.string.InitialActivity_invalid_URL), Toast.LENGTH_LONG).show();
            return;
        } else {

            shadowUrl = candidateURL;

            // generate a random "master key"
            byte[] masterKey = new byte[32];
            SecureRandom random = new SecureRandom();
            random.nextBytes(masterKey);

 //           DatabaseFactory.getConfigDatabase(this).setConfigById(shadowUrl, 1);
            TextSecurePreferences.setShadowServerUrl(this, shadowUrl);
 // set just a filler for now
            TextSecurePreferences.setMasterKey(this, masterKey);
            Log.i(TAG, "server URL added to app preferences");
            ((ApplicationContext) getApplication()).setServerSet(true);
            EventBus.getDefault().post(new ServerSetEvent());

            startActivity(new Intent(this, MainActivity.class));
            finish();
        }

    }

}
