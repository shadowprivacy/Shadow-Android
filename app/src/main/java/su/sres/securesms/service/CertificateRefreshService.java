package su.sres.securesms.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import su.sres.securesms.keyvalue.SignalStore;
import su.sres.securesms.logging.Log;
import su.sres.securesms.push.AccountManagerFactory;
import su.sres.securesms.push.SignalServiceTrustStore;
import su.sres.signalservice.api.SignalServiceAccountManager;
import su.sres.signalservice.api.messages.calls.SystemCertificates;
import su.sres.signalservice.api.push.TrustStore;

import static su.sres.securesms.InitialActivity.TRUSTSTORE_FILE_NAME;

/**
 * This one periodically probes the server for the current version of the certificate set and updates certificates locally if the server's version is newer.
 * After the update, it scans the truststore for expired certificates and removes those. If no new version is available from the server, this scan is not performed,
 * which allows for uninterrupted work with expired certificates on the system servers.
 */

public class CertificateRefreshService extends Service {

    private static final String TAG = CertificateRefreshService.class.getSimpleName();

    private static CertificateRefreshService myInstance = null;

    private Timer     serviceTimer;
    private TimerTask serviceTimerTask;

    private final long cycle = 21600000L;

    public final static String[] CERT_ALIASES = {
            "cloud_a",
            "cloud_b",
            "shadow_a",
            "shadow_b",
            "storage_a",
            "storage_b"
    };

   SignalServiceAccountManager accountManager;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Received start id " + startId + ": " + intent);

        UUID uuid = UUID.fromString(intent.getStringExtra("UUID"));
        String e164number = intent.getStringExtra("e164number");
        String password = intent.getStringExtra("password");

        accountManager = AccountManagerFactory.createAuthenticated(this, uuid, e164number, password);
        launchRefresh();

        return START_NOT_STICKY;
    }

    @Override
    public void onCreate() {
        myInstance = this;
    }

    @Override
    public void onDestroy() {

        serviceTimerTask.cancel();
        serviceTimer.cancel();
        myInstance = null;
        Log.i(TAG, "Exiting");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void launchRefresh() {
        serviceTimer = new Timer();

        serviceTimer.scheduleAtFixedRate(serviceTimerTask = new TimerTask() {

                    @Override
                    public void run() {
                        performRefresh();
                    }
                },

                0, cycle);
    }

    private void performRefresh() {

        int localVersion = SignalStore.serviceConfigurationValues().getCurrentCertVer();

        try {

        int remoteVersion = accountManager.getCertVer().getCertsVersion();
        if (remoteVersion > localVersion) {

            Log.i(TAG, "The server reports a newer version of system certificates set. Proceeding to import.");

            SystemCertificates receivedCerts = accountManager.getSystemCerts();

            byte [][] receivedCertBytes = {
                    receivedCerts.getCloudCertA(),
                    receivedCerts.getCloudCertB(),
                    receivedCerts.getShadowCertA(),
                    receivedCerts.getShadowCertB(),
                    receivedCerts.getStorageCertA(),
                    receivedCerts.getStorageCertB()
            };

            TrustStore trustStore = new SignalServiceTrustStore(this);
            char[] shadowStorePassword = trustStore.getKeyStorePassword().toCharArray();
            KeyStore shadowStore;

            try (InputStream keyStoreInputStream = trustStore.getKeyStoreInputStream()) {
                shadowStore = KeyStore.getInstance("BKS");
                shadowStore.load(keyStoreInputStream, shadowStorePassword);

                CertificateFactory certFactory = CertificateFactory.getInstance("X.509");

                for (int i = 0; i < 6; i++) {

                    if (receivedCertBytes[i] != null ) {

                        InputStream certInputStream = new ByteArrayInputStream(receivedCertBytes[i]);
                        X509Certificate cert = (X509Certificate) certFactory.generateCertificate(certInputStream);

                        shadowStore.setCertificateEntry(CERT_ALIASES[i], cert);
                        Log.i(TAG, CERT_ALIASES[i] + " added");
                    } else {
                        Log.i(TAG, CERT_ALIASES[i] + " is missing. Skipping.");
                    }
                }

                Log.i(TAG, "Certificates imported successfully. Proceeding to cleanup.");

                for (int i = 0; i < 6; i++) {

                    if (shadowStore.containsAlias(CERT_ALIASES[i])) {

                        X509Certificate trustStoreEntry = (X509Certificate) shadowStore.getCertificate(CERT_ALIASES[i]);

                        try {
                            trustStoreEntry.checkValidity();

                            // removing certs which are still valid but not present in the most current set
                            if (receivedCertBytes[i] == null) {
                                Log.i(TAG, CERT_ALIASES[i] + " not present in the most current set. Removing.");
                                shadowStore.deleteEntry(CERT_ALIASES[i]);
                            }

                        } catch (CertificateNotYetValidException e) {
                            Log.i(TAG, CERT_ALIASES[i] + " not yet valid. That is fine!");
                        }
                          catch (CertificateExpiredException e) {
                            Log.i(TAG, CERT_ALIASES[i] + " is expired! Removing.");
                            shadowStore.deleteEntry(CERT_ALIASES[i]);
                        }
                    }
                }

                Log.i(TAG, "Certificate cleanup complete");

                keyStoreInputStream.close();

                try (FileOutputStream fos = this.openFileOutput(TRUSTSTORE_FILE_NAME, Context.MODE_PRIVATE)) {
                    shadowStore.store(fos, shadowStorePassword);
                }

            } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException e) {
                Log.w(TAG, "Exception occurred while refreshing system certificates");
            }


            SignalStore.serviceConfigurationValues().setCurrentCertVer(remoteVersion);
            Log.i(TAG, "Successfully updated certificates to version " + remoteVersion);

        } else {
            Log.i(TAG, "No change in the certificates version. Skipping.");
        }

    } catch (IOException e) {
            Log.w(TAG, "IOException while trying to refresh certificates. Skipping.");
        }
    }

    public static boolean isServiceCreated() {
        try {
            // If instance was not cleared but the service was destroyed an Exception will be thrown
            return myInstance != null && myInstance.ping();
        } catch (NullPointerException e) {
            // destroyed/not-started
            return false;
        }
    }

    private boolean ping() {
        return true;
    }
}