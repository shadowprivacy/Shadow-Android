package su.sres.securesms;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.util.Pair;
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
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;

import java.security.cert.Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import su.sres.securesms.components.camera.CameraView;
import su.sres.securesms.events.ServerCertErrorEvent;
import su.sres.securesms.events.ServerSetEvent;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.securesms.logging.Log;
import su.sres.securesms.permissions.Permissions;
import su.sres.securesms.qr.ScanListener;
import su.sres.securesms.qr.ScanningThread;
import su.sres.securesms.util.Base64;
import su.sres.securesms.util.Util;
import su.sres.securesms.util.ViewUtil;

public class InitialActivity extends AppCompatActivity implements OnClickListener, ScanListener, ServiceConfigurationSetupListener {

    private static final String TAG = InitialActivity.class.getSimpleName();

    private static final String SERVICE_URI_COLUMN = "SRVURL";
    private static final String CLOUD_URI_COLUMN = "CLDURL";
    private static final String STORAGE_URI_COLUMN = "STRURL";
    private static final String UNIDENTIFIED_DELIVERY_CA_PUBLIC_KEY_COLUMN = "UDCA";
    private static final String SERVER_PUBLIC_KEY_HASH_COLUMN = "SRVPKH";

    private static final String EXAMPLE_HASH = "sha256/example";
    private static final String NULL_HASH = "sha256/null";

    public static final String TRUSTSTORE_FILE_NAME = "shadow.store";
    private static final String SHADOW_SERVER_CERT_ALIAS = "shadowcert";


    private InitialActivity.InitializationFragment initializationFragment = new InitialActivity.InitializationFragment();
    private InitialActivity.VerifyScanFragment scanFragment = new InitialActivity.VerifyScanFragment();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate()");

        if (SignalStore.registrationValues().isServerSet()) {
            Log.i(TAG, "The server is already set up, quitting the activity");
            startActivity(new Intent(this, MainActivity.class));
            finish();
        } else {
            Log.i(TAG, "The server is not set up, proceeding to set its address and import its certificate");

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
                       serverPublicKeyHash;

        private View container;
        private Button buttonScan;

        private OnClickListener clickListener;
        private ServiceConfigurationSetupListener callback;

        @Override public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);
            app = ((ApplicationContext) getActivity().getApplication());
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            EventBus.getDefault().register(this);
        }

        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup viewGroup, Bundle bundle) {
            this.container = ViewUtil.inflate(inflater, viewGroup, R.layout.initialization_fragment);
            this.buttonScan = ViewUtil.findById(container, R.id.buttonScan);

            this.buttonScan.setOnClickListener(clickListener);

            return container;
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            EventBus.getDefault().unregister(this);
        }

        void analyzeQrCode(String scanned) {

            int serviceUrlIndex,
                serverPublicKeyHashIndex;

            try {
                CSVParser qrparser = CSVParser.parse(scanned, CSVFormat.RFC4180.withHeader());
                List<String> csvHeaderList = qrparser.getHeaderNames();

                if (!csvHeaderList.contains(SERVICE_URI_COLUMN)) {
                    Toast.makeText(getActivity(), R.string.InitialActivity_qr_code_invalid, Toast.LENGTH_LONG).show();
                } else {

                    serviceUrlIndex = csvHeaderList.indexOf(SERVICE_URI_COLUMN);
                    serverPublicKeyHashIndex = csvHeaderList.indexOf(SERVER_PUBLIC_KEY_HASH_COLUMN);

                    List<CSVRecord> csvRecordList = qrparser.getRecords();
                    serviceUrl = csvRecordList.get(0).get(serviceUrlIndex);

                    if (serverPublicKeyHashIndex != -1) {
                        serverPublicKeyHash = csvRecordList.get(0).get(serverPublicKeyHashIndex);
                    } else {
                        serverPublicKeyHash = EXAMPLE_HASH;
                    }

                    if (serviceUrl != null) {

                        if (validateServiceUrls(serviceUrl)) {

                            SignalStore.serviceConfigurationValues().setShadowUrl(serviceUrl);
                            Log.i(TAG, "Server URL added to Signal Store");

                            new Thread(() -> {

                                X509Certificate candidateCert = null;
                                String hash = NULL_HASH;

                                try {
                                    candidateCert = probeServerCert();
                                } catch (ServerCertProbeException e) {
                                    Log.w(TAG, "Attempt to probe the server for a certificate failed with exception");
                                }

                                if (candidateCert != null) {

                                    try {
                                        candidateCert.checkValidity();

                                        try {
                                            hash = "sha256/" + calculatePublicKeyHash(candidateCert.getPublicKey().getEncoded());

                                            if (hash.equals(serverPublicKeyHash)) {

                                                try(FileOutputStream fos = getActivity().openFileOutput(TRUSTSTORE_FILE_NAME, Context.MODE_PRIVATE)) {

                                                    Pair tuple = initializeKeyStore(candidateCert);

                                                    ((KeyStore) tuple.first).store(fos, ((String) tuple.second).toCharArray());

                                                    SignalStore.registrationValues().setStorePass((String) tuple.second);

                                                    EventBus.getDefault().post(new ServerSetEvent());
                                                } catch (IOException e) {
                                                    Log.e (TAG, e);
                                                } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
                                                    Log.w(TAG, e);
                                                    EventBus.getDefault().post(new ServerCertErrorEvent(R.string.InitialActivity_server_certificate_import_error));
                                                }

                                            } else {
                                                Log.w(TAG, "WARNING! Shadow server public key hash mismatch");
                                                EventBus.getDefault().post(new ServerCertErrorEvent(R.string.InitialActivity_server_pub_key_hash_mismatch));
                                            }

                                        } catch (NoSuchAlgorithmException e) {
                                            Log.w(TAG, e);
                                        }

                                    } catch (CertificateExpiredException | CertificateNotYetValidException e) {
                                        Log.w(TAG, "The server certificate is expired or not yet valid");
                                        EventBus.getDefault().post(new ServerCertErrorEvent(R.string.InitialActivity_server_certificate_validity_error));
                                    }


                                } else {
                                    EventBus.getDefault().post(new ServerCertErrorEvent(R.string.InitialActivity_error_obtaining_the_server_certificate));
                                }
                            }).start();

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

        private boolean validateServiceUrls(String shadowUrl) {

            if (isUrlInvalid(shadowUrl)) {
                Log.w(TAG, "Shadow service URL is invalid");
                return false;
            } else {
                return true;
            }
        }

        boolean isUrlInvalid(String url) {
            String[] schemes = {"https"};
            UrlValidator validator = new UrlValidator(schemes, 4L);

            return !validator.isValid(url);
        }

        private X509Certificate probeServerCert() throws ServerCertProbeException {
            String hostname = SignalStore.serviceConfigurationValues().getShadowUrl();
            OkHttpClient probeClient;

            try {
                 probeClient = getUnsafeOkHttpClient();

            } catch (UnsafeOkHttpClientException e) {
                throw new ServerCertProbeException();
            }

            Request request = new Request.Builder()
                   .url(hostname)
                   .build();

            Response response;
            List<Certificate> peerCertList;

            try {
                response = probeClient.newCall(request).execute();
                peerCertList = response.handshake().peerCertificates();
                response.close();
            } catch(IOException e) {
                Log.w(TAG, "Failed to extract the server certificate");
                throw new ServerCertProbeException();
            }
                return (X509Certificate) peerCertList.get(0);
        }

        private OkHttpClient getUnsafeOkHttpClient() throws UnsafeOkHttpClientException {
            try {
                // Create a trust manager that does not validate certificate chains
                final TrustManager[] trustAllCerts = new TrustManager[] {
                        new X509TrustManager() {

                            @Override
                            public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                            }

                            @Override
                            public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                            }

                            @Override
                            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                                return new java.security.cert.X509Certificate[]{};
                            }
                        }
                };

                // Install the all-trusting trust manager
                final SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
                // Create an ssl socket factory with our all-trusting manager
                final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

                return new OkHttpClient.Builder().sslSocketFactory(sslSocketFactory, (X509TrustManager)trustAllCerts[0])
                                                 .build();

            } catch (Exception e) {
                Log.w(TAG, "Failed to construct unsafe OkHttp client");
                throw new UnsafeOkHttpClientException();
            }
        }

        private Pair initializeKeyStore(X509Certificate shadowcert) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
            byte[] shadowStoreRandom = new byte[32];
            SecureRandom random = new SecureRandom();
            random.nextBytes(shadowStoreRandom);
            String shadowStorePassword = Base64.encodeBytes(shadowStoreRandom);

            KeyStore shadowStore = KeyStore.getInstance("BKS");
            shadowStore.load(null, shadowStorePassword.toCharArray());
            shadowStore.setCertificateEntry(SHADOW_SERVER_CERT_ALIAS, shadowcert);

            return new Pair<>(shadowStore, shadowStorePassword);
        }

        private static String calculatePublicKeyHash(byte[] key) throws NoSuchAlgorithmException {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            return Base64.encodeBytes(messageDigest.digest(key));
        }

        @Subscribe(threadMode = ThreadMode.MAIN)
        public void onEventServerCertError(ServerCertErrorEvent event) {
            Toast.makeText(getActivity(), event.message, Toast.LENGTH_LONG).show();
        }

        @Subscribe(threadMode = ThreadMode.MAIN)
        public void onEventServerSet(ServerSetEvent event) {

            SignalStore.registrationValues().setServerSet(true);
            callback.onServiceConfigurationSet();
        }
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

    private static class UnsafeOkHttpClientException extends Exception {}

    private static class ServerCertProbeException extends Exception {}
}