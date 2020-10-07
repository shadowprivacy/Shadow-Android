package su.sres.securesms.jobs;

import android.content.Context;

import androidx.annotation.NonNull;

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
import java.util.concurrent.TimeUnit;

import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobmanager.impl.NetworkConstraint;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.securesms.logging.Log;
import su.sres.securesms.push.SignalServiceTrustStore;
import su.sres.signalservice.api.SignalServiceAccountManager;
import su.sres.signalservice.api.messages.calls.SystemCertificates;
import su.sres.signalservice.api.push.TrustStore;
import su.sres.signalservice.api.push.exceptions.PushNetworkException;

import static su.sres.securesms.InitialActivity.TRUSTSTORE_FILE_NAME;

public class CertificateRefreshJob extends BaseJob {

    public static final String KEY = "CertificateRefreshJob";

    private static final String TAG = CertificateRefreshJob.class.getSimpleName();

    private static final long REFRESH_INTERVAL = TimeUnit.HOURS.toMillis(1);

    public final static String[] CERT_ALIASES = {
            "cloud_a",
            "cloud_b",
            "shadow_a",
            "shadow_b",
            "storage_a",
            "storage_b"
    };

    private final Context                     context;
    private final SignalServiceAccountManager accountManager;

    public CertificateRefreshJob()
    {
        this(new Job.Parameters.Builder()
                        .setQueue("CertificateRefreshJob")
                        .addConstraint(NetworkConstraint.KEY)
                        // default
                        .setMaxAttempts(1)
                        .setMaxInstances(1)
                        .build()
        );
    }

    private CertificateRefreshJob(@NonNull Job.Parameters parameters) {
        super(parameters);

        this.context        = ApplicationDependencies.getApplication();
        this.accountManager = ApplicationDependencies.getSignalServiceAccountManager();
    }

    @Override
    public @NonNull
    Data serialize() {
        return Data.EMPTY;
    }

    @Override
    public @NonNull String getFactoryKey() {
        return KEY;
    }

    @Override
    public void onRun() throws IOException {
        Log.i(TAG, "CertificateRefreshJob.onRun()");

        performRefresh();
    }

    @Override
    public boolean onShouldRetry(@NonNull Exception exception) {
        if (exception instanceof PushNetworkException) return true;
        return false;
    }

    public static void scheduleIfNecessary() {
        long timeSinceLastRefresh = System.currentTimeMillis() - SignalStore.misc().getLastCertRefreshTime();

        if (timeSinceLastRefresh > REFRESH_INTERVAL) {
            Log.i(TAG, "Scheduling a certificate refresh. Time since last schedule: " + timeSinceLastRefresh + " ms");
            ApplicationDependencies.getJobManager().add(new CertificateRefreshJob());
        } else {
            Log.i(TAG, "Holdtime not expired. Skipping.");
        }
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

                TrustStore trustStore = new SignalServiceTrustStore(context);
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

                    try (FileOutputStream fos = context.openFileOutput(TRUSTSTORE_FILE_NAME, Context.MODE_PRIVATE)) {
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

            SignalStore.misc().setLastCertRefreshTime(System.currentTimeMillis());

        } catch (IOException e) {
            Log.w(TAG, "IOException while trying to refresh certificates. Skipping.");
        }
    }

    @Override
    public void onFailure() { }

    public static final class Factory implements Job.Factory<CertificateRefreshJob> {

        @Override
        public @NonNull CertificateRefreshJob create(@NonNull Parameters parameters, @NonNull Data data) {
            return new CertificateRefreshJob(parameters);
        }
    }
}