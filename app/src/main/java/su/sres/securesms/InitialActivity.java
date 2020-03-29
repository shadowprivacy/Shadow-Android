package su.sres.securesms;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import android.os.Vibrator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.validator.routines.UrlValidator;
import org.apache.commons.csv.CSVParser;

import java.io.IOException;
import java.util.List;

import su.sres.securesms.components.camera.CameraView;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.securesms.logging.Log;
import su.sres.securesms.permissions.Permissions;
import su.sres.securesms.qr.ScanListener;
import su.sres.securesms.qr.ScanningThread;
import su.sres.securesms.util.Util;
import su.sres.securesms.util.ViewUtil;

import static su.sres.securesms.keyvalue.ServiceConfigurationValues.EXAMPLE_URI;

public class InitialActivity extends AppCompatActivity implements OnClickListener, ScanListener, ServiceConfigurationSetupListener {

    private static final String TAG = InitialActivity.class.getSimpleName();

    private static final String SERVICE_URI_COLUMN = "SRVURL";
    private static final String CLOUD_URI_COLUMN = "CLDURL";
    private static final String STORAGE_URI_COLUMN = "STRURL";
    private static final String UNIDENTIFIED_DELIVERY_CA_PUBLIC_KEY_COLUMN = "UDCA";
    private static final String SERVER_CA_PUBLIC_COLUMN = "SRVCA";

    private InitialActivity.InitializationFragment initializationFragment = new InitialActivity.InitializationFragment();
    private InitialActivity.VerifyScanFragment scanFragment = new InitialActivity.VerifyScanFragment();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate()");

        if (((ApplicationContext) getApplication()).getServiceConfigurationSet()) {
            Log.i(TAG, "the server URL is already set up, quitting the activity");
            startActivity(new Intent(this, MainActivity.class));
            finish();
        } else {
            Log.i(TAG, "the server URL is a default one, proceeding to set the real value");

            scanFragment.setScanListener(this);
            initializationFragment.setClickListener(this);
            initializationFragment.setServiceConfigurationSetupListener(this);

            getSupportFragmentManager().beginTransaction()
                    .add(android.R.id.content, initializationFragment)
                    .addToBackStack(null)
                    .commitAllowingStateLoss();
        }
    }

    @Override
    public void onQrDataFound(final String data) {
        Util.runOnMain(() -> {
            ((Vibrator) getSystemService(Context.VIBRATOR_SERVICE)).vibrate(50);

            // return back to Initialization Fragment
            getSupportFragmentManager().popBackStack();

            initializationFragment.analyzeQrCode(data);
        });
    }

    // this is invoked when the user taps the Scan button
    @Override
    public void onClick(View v) {
        Permissions.with(this)
                .request(Manifest.permission.CAMERA)
                .ifNecessary()
                .withPermanentDenialDialog(getString(R.string.VerifyIdentityActivity_signal_needs_the_camera_permission_in_order_to_scan_a_qr_code_but_it_has_been_permanently_denied))
                .onAllGranted(() -> {
                    FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

                    transaction.replace(android.R.id.content, scanFragment)
                            .addToBackStack(null)
                            .commitAllowingStateLoss();
                })
                .onAnyDenied(() -> Toast.makeText(this, R.string.VerifyIdentityActivity_unable_to_scan_qr_code_without_camera_permission, Toast.LENGTH_LONG).show())
                .execute();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy()");
        super.onDestroy();
    }

/**    @Override
    public void onAttachFragment(Fragment fragment) {
        if (fragment instanceof InitializationFragment) {
            InitializationFragment initializationFragment = (InitializationFragment) fragment;
            initializationFragment.setServiceConfigurationSetupListener(this);
        }
    }  */

    @Override
    public void onServiceConfigurationSet() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    public static class InitializationFragment extends Fragment {

        private ApplicationContext app;

        private String serviceUrl,
 //                      cloudUrl,
 //                      storageUrl,
 //                      unidentifiedDeliveryCaPublicKey,
                       serverCaPublicKey;

        private View container;
        private Button buttonScan;

        private OnClickListener clickListener;
        private ServiceConfigurationSetupListener callback;

        @Override public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);
            app = ((ApplicationContext) getActivity().getApplication());
        }

        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup viewGroup, Bundle bundle) {
            this.container = ViewUtil.inflate(inflater, viewGroup, R.layout.initialization_fragment);
            this.buttonScan = ViewUtil.findById(container, R.id.buttonScan);

            this.buttonScan.setOnClickListener(clickListener);

            return container;
        }

        void analyzeQrCode(String scanned) {

            int serviceUrlIndex,
 //                   cloudUrlIndex,
 //                   storageUrlIndex,
 //                   unidentifiedDeliveryCaPublicKeyIndex,
                    serverCaPublicKeyIndex;

            Log.w(TAG, "The scanned QR code is: " + scanned);

            try {
                CSVParser qrparser = CSVParser.parse(scanned, CSVFormat.RFC4180.withHeader());

                List<String> csvHeaderList = qrparser.getHeaderNames();

                if (!csvHeaderList.contains(SERVICE_URI_COLUMN)) {

                    Toast.makeText(getActivity(), R.string.InitialActivity_qr_code_invalid, Toast.LENGTH_LONG).show();
                } else {

                    serviceUrlIndex = csvHeaderList.indexOf(SERVICE_URI_COLUMN);
//                    cloudUrlIndex = csvHeaderList.indexOf(CLOUD_URI_COLUMN);
//                    storageUrlIndex = csvHeaderList.indexOf(STORAGE_URI_COLUMN);
//                    unidentifiedDeliveryCaPublicKeyIndex = csvHeaderList.indexOf(UNIDENTIFIED_DELIVERY_CA_PUBLIC_KEY_COLUMN);
                    serverCaPublicKeyIndex = csvHeaderList.indexOf(SERVER_CA_PUBLIC_COLUMN);

                    List<CSVRecord> csvRecordList = qrparser.getRecords();
                    serviceUrl = csvRecordList.get(0).get(serviceUrlIndex);

/**                    if (cloudUrlIndex != -1) {
                        cloudUrl = csvRecordList.get(0).get(cloudUrlIndex);
                    } else {
                        cloudUrl = EXAMPLE_URI;
                    }

                    if (storageUrlIndex != -1) {
                        storageUrl = csvRecordList.get(0).get(storageUrlIndex);
                    } else {
                        storageUrl = EXAMPLE_URI;
                    } */

                    Log.w(TAG, "The server URL is: " + serviceUrl);
 //                   Log.w(TAG, "The cloud URL is: " + cloudUrl);
 //                   Log.w(TAG, "The storage URL is: " + storageUrl);

                    if (serviceUrl != null

                        //  && !cloudUrl.equals(EXAMPLE_URI)
                        //  && !storageUrl.equals(EXAMPLE_URI)
                    ) {
                        if (validateServiceUrls(serviceUrl
                        //        , cloudUrl, storageUrl
                        )) {

                            SignalStore.serviceConfigurationValues().setShadowUrl(serviceUrl);
                            // SignalStore.serviceConfigurationValues().setCloudUrl(cloudUrl);
                            // SignalStore.serviceConfigurationValues().setStorageUrl(storageUrl);

                            Log.i(TAG, "service URLs added to Signal Store");
                            app.setServiceConfigurationSet(true);

                            callback.onServiceConfigurationSet();

                        } else {
                            Toast.makeText(getActivity(), getString(R.string.InitialActivity_invalid_URL), Toast.LENGTH_LONG).show();
                        }

                    } else {
                        Toast.makeText(getActivity(), R.string.InitialActivity_qr_code_invalid, Toast.LENGTH_LONG).show();
                    }
                }

            } catch (IOException e) {
                Log.w(TAG, e);
                Toast.makeText(getActivity(), R.string.InitialActivity_qr_code_unparseable, Toast.LENGTH_LONG).show();
            }
        }

        void setClickListener(OnClickListener listener) {
            this.clickListener = listener;
        }

        void setServiceConfigurationSetupListener(ServiceConfigurationSetupListener callback) {
            this.callback = callback;
        }

        private boolean validateServiceUrls(String shadowUrl
 //               , String cloudUrl, String storageUrl
        ) {

            if (isUrlInvalid(shadowUrl)) {
                Log.w(TAG, "Shadow service URL is invalid");
                return false;
 /**           } else if (isUrlInvalid(cloudUrl)) {
                Log.w(TAG, "Cloud URL is invalid");
                return false;
            } else if (isUrlInvalid(storageUrl)) {
                Log.w(TAG, "Storage URL is invalid");
                return false;  */
            } else {
                return true;
            }
        }

        boolean isUrlInvalid(String url) {

            String[] schemes = {"https"};
            UrlValidator validator = new UrlValidator(schemes, 4L);

            return !validator.isValid(url);
        }

        /**       @Override public void onClick(View v) {

        // here we shall record the scanned values into preferences or Signal Store, and proceed to Main Activity

        Log.w (TAG, "Button Clicked");

        String candidateURL = "https://" + shadowAddress.getText().toString() + ":" + shadowPort.getText().toString();

        if (!validator.isValid(candidateURL)) {
        Toast.makeText(this, getString(R.string.InitialActivity_invalid_URL), Toast.LENGTH_LONG).show();
        return;
        } else {

        shadowUrl = candidateURL;

        //           DatabaseFactory.getConfigDatabase(this).setConfigById(shadowUrl, 1);
        TextSecurePreferences.setShadowServerUrl(this, shadowUrl);

        Log.i(TAG, "server URL added to app preferences");
        ((ApplicationContext) getApplication()).setServerSet(true);

        startActivity(new Intent(this, MainActivity.class));
        finish();
        }



        } */
    }

    public static class VerifyScanFragment extends Fragment {

        private View container;
        private CameraView cameraView;
        private ScanningThread scanningThread;
        private ScanListener scanListener;

        public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup viewGroup, Bundle bundle) {
            this.container = ViewUtil.inflate(inflater, viewGroup, R.layout.verify_scan_fragment);
            this.cameraView = ViewUtil.findById(container, R.id.scanner);

            return container;
        }

        @Override
        public void onResume() {
            super.onResume();
            this.scanningThread = new ScanningThread();
            this.scanningThread.setScanListener(scanListener);
            this.scanningThread.setCharacterSet("ISO-8859-1");
            this.cameraView.onResume();
            this.cameraView.setPreviewCallback(scanningThread);
            this.scanningThread.start();
        }

        @Override
        public void onPause() {
            super.onPause();
            this.cameraView.onPause();
            this.scanningThread.stopScanning();
        }

        @Override
        public void onConfigurationChanged(Configuration newConfiguration) {
            super.onConfigurationChanged(newConfiguration);
            this.cameraView.onPause();
            this.cameraView.onResume();
            this.cameraView.setPreviewCallback(scanningThread);
        }

        public void setScanListener(ScanListener listener) {
            if (this.scanningThread != null) scanningThread.setScanListener(listener);
            this.scanListener = listener;
        }
    }

}


