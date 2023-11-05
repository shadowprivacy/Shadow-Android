package su.sres.securesms;

import android.Manifest;
import android.animation.Animator;
import android.content.Context;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
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
import org.apache.commons.csv.CSVParser;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.SecureRandom;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.List;

import java.security.cert.Certificate;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import su.sres.core.util.ThreadUtil;
import su.sres.core.util.concurrent.SignalExecutors;
import su.sres.securesms.components.camera.CameraView;
import su.sres.securesms.events.ProxyErrorEvent;
import su.sres.securesms.events.ServerCertErrorEvent;
import su.sres.securesms.events.ServerSetEvent;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.core.util.logging.Log;
import su.sres.securesms.permissions.Permissions;
import su.sres.securesms.qr.ScanListener;
import su.sres.securesms.qr.ScanningThread;
import su.sres.securesms.util.NetworkConnectionStateListener;
import su.sres.securesms.util.Base64;
import su.sres.securesms.util.ShadowProxyUtil;
import su.sres.securesms.util.Util;
import su.sres.securesms.util.validator.UrlValidator;
import su.sres.securesms.util.ViewUtil;
import su.sres.signalservice.internal.configuration.ShadowProxy;

public class InitialActivity extends AppCompatActivity implements OnClickListener, ScanListener, ServiceConfigurationSetupListener {

    private static final String TAG = Log.tag(InitialActivity.class);

    private static final String FCM_SENDER_ID_COLUMN = "FCMSID";
    private static final String SERVER_CERT_HASH_COLUMN = "SRVCH";
    private static final String SERVICE_URI_COLUMN = "SRVURL";
    private static final String PROXY_COLUMN = "PRX";

    private static final String EXAMPLE_HASH = "sha256/example";

    public static final String TRUSTSTORE_FILE_NAME = "shadow.store";
    private static final String SHADOW_SERVER_CERT_ALIAS_A = "shadow_a";

    private InitialActivity.InitializationFragment initializationFragment = new InitialActivity.InitializationFragment();
    private InitialActivity.VerifyScanFragment scanFragment = new InitialActivity.VerifyScanFragment();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate()");

        if (SignalStore.registrationValues().isServerSet()) {
            Log.i(TAG, "The server is already set up, quitting the activity");
            startActivity(MainActivity.clearTop(this));
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
        ThreadUtil.runOnMain(() -> {
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

    @Override
    public void onServiceConfigurationSet() {
        startActivity(MainActivity.clearTop(this));
        finish();
    }

    public static class InitializationFragment extends Fragment implements NetworkConnectionStateListener.Callback {

        private ApplicationContext app;

        private String fcmSenderId,
                serviceUrl,
                serverCertHash,
                proxyHost;

        private View container;
        private Button buttonScan;
        private View serviceWarning;

        private OnClickListener clickListener;
        private ServiceConfigurationSetupListener callback;

        private NetworkConnectionStateListener networkConnectionListener;

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
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

            return container;
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            this.buttonScan = container.findViewById(R.id.buttonScan);
            this.buttonScan.setOnClickListener(clickListener);
            serviceWarning = view.findViewById(R.id.cell_service_warning);

            ConnectivityManager connManager = getContext().getSystemService(ConnectivityManager.class);

            networkConnectionListener = new NetworkConnectionStateListener(this, this, connManager);
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            EventBus.getDefault().unregister(this);
        }

        void analyzeQrCode(String scanned) {

            int fcmSenderIdIndex,
                    serviceUrlIndex,
                    serverCertHashIndex,
                    proxyIndex;

            try {
                CSVParser qrparser = CSVParser.parse(scanned, CSVFormat.RFC4180.withHeader());
                List<String> csvHeaderList = qrparser.getHeaderNames();

                if (!csvHeaderList.contains(SERVICE_URI_COLUMN)) {
                    Toast.makeText(getActivity(), R.string.InitialActivity_qr_code_invalid, Toast.LENGTH_LONG).show();
                } else {

                    serviceUrlIndex = csvHeaderList.indexOf(SERVICE_URI_COLUMN);
                    serverCertHashIndex = csvHeaderList.indexOf(SERVER_CERT_HASH_COLUMN);
                    fcmSenderIdIndex = csvHeaderList.indexOf(FCM_SENDER_ID_COLUMN);
                    proxyIndex = csvHeaderList.indexOf(PROXY_COLUMN);

                    List<CSVRecord> csvRecordList = qrparser.getRecords();
                    serviceUrl = csvRecordList.get(0).get(serviceUrlIndex);

                    if (serverCertHashIndex != -1) {
                        serverCertHash = csvRecordList.get(0).get(serverCertHashIndex);
                    } else {
                        serverCertHash = EXAMPLE_HASH;
                    }

                    if (fcmSenderIdIndex != -1) {
                        fcmSenderId = csvRecordList.get(0).get(fcmSenderIdIndex);
                    }

                    if (proxyIndex != -1) {
                        proxyHost = csvRecordList.get(0).get(proxyIndex);
                        if (!validateServiceUrls("https://" + proxyHost)) {
                            Log.w(TAG, "Proxy configuration is invalid, ignoring");
                            proxyHost = null;
                        } else {
                            Log.i(TAG, "Proxy configuration found, testing...");
                            enableProxy(proxyHost);
                        }
                    } else {
                        Log.i(TAG, "Proxy configuration not found, ignoring");
                    }

                    if (serviceUrl != null) {

                        if (validateServiceUrls(serviceUrl)) {

                            SignalStore.serviceConfigurationValues().setShadowUrl(serviceUrl);
                            SignalStore.serviceConfigurationValues().setFcmSenderId(fcmSenderId);
                            Log.i(TAG, "Server URL and Sender ID added to Signal Store");

                            new Thread(() -> {

                                X509Certificate candidateCert = null;
                                String hash;

                                try {
                                    candidateCert = probeServerCert();
                                } catch (ServerCertProbeException e) {
                                    Log.w(TAG, "Attempt to probe the server for a certificate failed with exception");
                                    if (SignalStore.proxy().isProxyEnabled()) {
                                        Log.w(TAG, "The proxy does not seem to be operable, disabling");
                                        ShadowProxyUtil.disableProxy();
                                    }
                                }

                                if (candidateCert != null) {

                                    try {
                                        candidateCert.checkValidity();

                                        String commonName = extractCommonName(candidateCert.getSubjectDN());

                                        Pattern pattern = Pattern.compile("://(.*?):");
                                        Matcher matcher = pattern.matcher(SignalStore.serviceConfigurationValues().getShadowUrl());

                                        if (matcher.find()) {

                                            if (!commonName.equals(matcher.group(1))) {
                                                throw new CertificateInvalidCNException();
                                            }
                                        }

                                        try {
                                            hash = "sha256/" + calculateCertHash(candidateCert.getEncoded());

                                            if (hash.equals(serverCertHash)) {

                                                try (FileOutputStream fos = getActivity().openFileOutput(TRUSTSTORE_FILE_NAME, Context.MODE_PRIVATE)) {

                                                    Pair tuple = initializeKeyStore(candidateCert);

                                                    ((KeyStore) tuple.first).store(fos, ((String) tuple.second).toCharArray());

                                                    SignalStore.registrationValues().setStorePass((String) tuple.second);

                                                    EventBus.getDefault().post(new ServerSetEvent());
                                                } catch (IOException e) {
                                                    Log.e(TAG, e);
                                                } catch (KeyStoreException |
                                                         NoSuchAlgorithmException |
                                                         CertificateException e) {
                                                    Log.w(TAG, e);
                                                    EventBus.getDefault().post(new ServerCertErrorEvent(R.string.InitialActivity_server_certificate_import_error));
                                                }

                                            } else {
                                                Log.w(TAG, "WARNING! Shadow server certificate hash mismatch");
                                                EventBus.getDefault().post(new ServerCertErrorEvent(R.string.InitialActivity_server_cert_hash_mismatch));
                                            }

                                        } catch (NoSuchAlgorithmException |
                                                 CertificateEncodingException e) {
                                            Log.w(TAG, e);
                                        }

                                    } catch (CertificateExpiredException |
                                             CertificateNotYetValidException e) {
                                        Log.w(TAG, "The server certificate is expired or not yet valid");
                                        EventBus.getDefault().post(new ServerCertErrorEvent(R.string.InitialActivity_server_certificate_validity_error));
                                    } catch (CertificateInvalidCNException e) {
                                        Log.w(TAG, "The server certificate's CN is invalid");
                                        EventBus.getDefault().post(new ServerCertErrorEvent(R.string.InitialActivity_server_certificate_CN_error));
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

        private boolean validateServiceUrls(String url) {

            if (isUrlInvalid(url)) {
                Log.w(TAG, "Service URL is invalid");
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

        @WorkerThread
        private void enableProxy(String host) {

            SignalExecutors.UNBOUNDED.execute(() -> {

                try {
                    final URL url = new URL("http://" + host + "/ping");
                    final HttpURLConnection conn =  (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.connect();
                    int code = conn.getResponseCode();
                    conn.getInputStream().close();
                    if (code == 200) {
                        Log.i(TAG, "Proxy accessible, enabling");
                        ShadowProxyUtil.enableProxy(new ShadowProxy(host, 443));
                    } else {
                        Log.w(TAG, "Proxy inaccessible, will not enable");
                        EventBus.getDefault().post(new ProxyErrorEvent(R.string.InitialActivity_proxy_inaccessible));
                    }
                } catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    Log.w(TAG, "Proxy inaccessible, will not enable");
                    EventBus.getDefault().post(new ProxyErrorEvent(R.string.InitialActivity_proxy_inaccessible));
                }
            });
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
            } catch (IOException e) {
                Log.w(TAG, "Failed to extract the server certificate");
                throw new ServerCertProbeException();
            }
            return (X509Certificate) peerCertList.get(0);
        }

        private OkHttpClient getUnsafeOkHttpClient() throws UnsafeOkHttpClientException {
            try {
                // Create a trust manager that does not validate certificate chains
                final TrustManager[] trustAllCerts = new TrustManager[]{
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

                return new OkHttpClient.Builder().sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0])
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
            shadowStore.setCertificateEntry(SHADOW_SERVER_CERT_ALIAS_A, shadowcert);

            return new Pair<>(shadowStore, shadowStorePassword);
        }

        private static String calculateCertHash(byte[] cert) throws NoSuchAlgorithmException {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            return Base64.encodeBytes(messageDigest.digest(cert));
        }

        private String extractCommonName(Principal principal) {

            int start = principal.getName().indexOf("CN");
            String tmpName, name = "";
            if (start >= 0) {
                tmpName = principal.getName().substring(start + 3);
                int end = tmpName.indexOf(",");
                if (end > 0) {
                    name = tmpName.substring(0, end);
                } else {
                    name = tmpName;
                }
            }

            return name;
        }

        @Subscribe(threadMode = ThreadMode.MAIN)
        public void onEventServerCertError(ServerCertErrorEvent event) {
            Toast.makeText(getActivity(), event.message, Toast.LENGTH_LONG).show();
        }

        @Subscribe(threadMode = ThreadMode.MAIN)
        public void onProxyError(ProxyErrorEvent event) {
            Toast.makeText(getActivity(), event.message, Toast.LENGTH_LONG).show();
        }

        @Subscribe(threadMode = ThreadMode.MAIN)
        public void onEventServerSet(ServerSetEvent event) {

            SignalStore.registrationValues().setServerSet(true);
            callback.onServiceConfigurationSet();
        }

        @Override
        public void onNoConnectionPresent() {
            if (serviceWarning.getVisibility() == View.VISIBLE) {
                return;
            }
            serviceWarning.setVisibility(View.VISIBLE);
            serviceWarning.animate()
                    .alpha(1)
                    .setListener(null)
                    .start();
        }

        @Override
        public void onConnectionPresent() {
            if (serviceWarning.getVisibility() != View.VISIBLE) {
                return;
            }
            serviceWarning.animate()
                    .alpha(0)
                    .setListener(new Animator.AnimatorListener() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            serviceWarning.setVisibility(View.GONE);
                        }

                        @Override
                        public void onAnimationStart(Animator animation) {
                        }

                        @Override
                        public void onAnimationCancel(Animator animation) {
                        }

                        @Override
                        public void onAnimationRepeat(Animator animation) {
                        }
                    })
                    .start();
        }

    }

    public static class VerifyScanFragment extends Fragment {

        private View container;
        private CameraView cameraView;
        private ScanningThread scanningThread;
        private ScanListener scanListener;

        public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup viewGroup, Bundle bundle) {
            this.container = ViewUtil.inflate(inflater, viewGroup, R.layout.verify_scan_fragment);
            this.cameraView = container.findViewById(R.id.scanner);

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

    private static class UnsafeOkHttpClientException extends Exception {
    }

    private static class ServerCertProbeException extends Exception {
    }

    private static class CertificateInvalidCNException extends Exception {
    }
}