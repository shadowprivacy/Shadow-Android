package su.sres.securesms.jobs;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import su.sres.securesms.R;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobmanager.impl.NetworkConstraint;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.core.util.logging.Log;
import su.sres.securesms.push.SignalServiceTrustStore;
import su.sres.signalservice.api.SignalServiceAccountManager;
import su.sres.signalservice.api.messages.calls.SystemCertificates;
import su.sres.signalservice.api.push.TrustStore;

import static su.sres.securesms.InitialActivity.TRUSTSTORE_FILE_NAME;

public class CertificatePullJob extends BaseJob {

    public static final String KEY = "CertificatePullJob";

    private static final String TAG = CertificatePullJob.class.getSimpleName();

    public final static String[] CERT_ALIASES = {
            "cloud_a",
            "cloud_b",
            "shadow_a",
            "shadow_b",
            "storage_a",
            "storage_b"
    };

    private final Context context;
    private final SignalServiceAccountManager accountManager;

    public CertificatePullJob() {
        this(new Job.Parameters.Builder()
                .setQueue("CertificatePullJob")
                .addConstraint(NetworkConstraint.KEY)
                // default
                .setMaxAttempts(1)
                .setMaxInstancesForFactory(1)
                .build()
        );
    }

    private CertificatePullJob(@NonNull Job.Parameters parameters) {
        super(parameters);

        this.context = ApplicationDependencies.getApplication();
        this.accountManager = ApplicationDependencies.getSignalServiceAccountManager();
    }

    @Override
    public @NonNull
    Data serialize() {
        return Data.EMPTY;
    }

    @Override
    public @NonNull
    String getFactoryKey() {
        return KEY;
    }

    @Override
    public void onRun() throws IOException {
        Log.i(TAG, "CertificateRefreshJob.onRun()");

        performPull();
    }

    @Override
    public boolean onShouldRetry(@NonNull Exception exception) {
        return false;
    }

    public static void scheduleIfNecessary() {
        Log.i(TAG, "Scheduling a certificate pull");
        ApplicationDependencies.getJobManager().add(new CertificatePullJob());
    }

    private void performPull() throws IOException {

        int localVersion = SignalStore.serviceConfigurationValues().getCurrentCertVer();

        try {

            int remoteVersion = accountManager.getCertVer().getCertsVersion();
            if (remoteVersion == localVersion) {

                Log.i(TAG, "The server reports the same version of system certificates set. Proceeding to pull.");

                SystemCertificates receivedCerts = accountManager.getSystemCerts();

                byte[][] receivedCertBytes = {
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

                        if (receivedCertBytes[i] != null) {

                            InputStream certInputStream = new ByteArrayInputStream(receivedCertBytes[i]);
                            X509Certificate cert = (X509Certificate) certFactory.generateCertificate(certInputStream);

                            shadowStore.setCertificateEntry(CERT_ALIASES[i], cert);
                            Log.i(TAG, CERT_ALIASES[i] + " added");
                        } else {
                            Log.i(TAG, CERT_ALIASES[i] + " is missing. Skipping.");
                        }
                    }

                    Log.i(TAG, "Certificates pulled successfully. Proceeding to cleanup.");

                    for (int i = 0; i < 6; i++) {

                        if (shadowStore.containsAlias(CERT_ALIASES[i])) {

                            // removing certs which are not present in the most current set
                            if (receivedCertBytes[i] == null) {
                                Log.i(TAG, CERT_ALIASES[i] + " not present in the most current set. Removing.");
                                shadowStore.deleteEntry(CERT_ALIASES[i]);
                            }
                        }
                    }

                    Log.i(TAG, "Certificate cleanup complete");

                    keyStoreInputStream.close();

                    try (FileOutputStream fos = context.openFileOutput(TRUSTSTORE_FILE_NAME, Context.MODE_PRIVATE)) {
                        shadowStore.store(fos, shadowStorePassword);
                    }

                    notifyUser(R.string.CertificatePull_job_success);

                } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException e) {
                    Log.w(TAG, "Exception occurred while pulling system certificates");
                }

            } else {
                Log.i(TAG, "The certs version reported by the server differs from local, use regular refresh instead. Skipping.");
            }

            SignalStore.misc().setLastCertRefreshTime(System.currentTimeMillis());

        } catch (IOException e) {
            Log.w(TAG, "IOException while trying to pull certificates. Skipping.");
            notifyUser(R.string.CertificatePull_job_failure);
            throw e;
        }
    }

    @Override
    public void onFailure() {
        Toast.makeText(context, R.string.CertificatePull_job_failure, Toast.LENGTH_LONG).show();
    }

    private void notifyUser(int message) {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context, message, Toast.LENGTH_LONG).show());
    }

    public static final class Factory implements Job.Factory<CertificatePullJob> {

        @Override
        public @NonNull
        CertificatePullJob create(@NonNull Parameters parameters, @NonNull Data data) {
            return new CertificatePullJob(parameters);
        }
    }
}