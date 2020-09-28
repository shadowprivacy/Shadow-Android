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
import su.sres.signalservice.api.messages.calls.ConfigurationInfo;
import su.sres.signalservice.api.messages.calls.SystemCertificates;
import su.sres.signalservice.api.push.TrustStore;
import su.sres.signalservice.api.push.exceptions.PushNetworkException;

import static su.sres.securesms.InitialActivity.TRUSTSTORE_FILE_NAME;

public class ServiceConfigRefreshJob extends BaseJob {

    public static final String KEY = "ServiceConfigRefreshJob";

    private static final String TAG = ServiceConfigRefreshJob.class.getSimpleName();

    private static final long REFRESH_INTERVAL = TimeUnit.DAYS.toMillis(1);

    private final Context                     context;
    private final SignalServiceAccountManager accountManager;

    public ServiceConfigRefreshJob()
    {
        this(new Job.Parameters.Builder()
                .setQueue("ServiceConfigRefreshJob")
                .addConstraint(NetworkConstraint.KEY)
                // default
                .setMaxAttempts(1)
                .setMaxInstances(1)
                .build()
        );
    }

    private ServiceConfigRefreshJob(@NonNull Job.Parameters parameters) {
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
        Log.i(TAG, "ServiceConfigRefreshJob.onRun()");

        performRefresh();
    }

    @Override
    public boolean onShouldRetry(@NonNull Exception exception) {
        if (exception instanceof PushNetworkException) return true;
        return false;
    }

    public static void scheduleIfNecessary() {
        long timeSinceLastRefresh = System.currentTimeMillis() - SignalStore.getLastServiceConfigRefreshTime();

        if (timeSinceLastRefresh > REFRESH_INTERVAL) {
            Log.i(TAG, "Scheduling a service configuration refresh. Time since last schedule: " + timeSinceLastRefresh + " ms");
            ApplicationDependencies.getJobManager().add(new ServiceConfigRefreshJob());
        } else {
            Log.i(TAG, "Holdtime not expired. Skipping.");
        }
    }

    private void performRefresh() {

        try {
                ConfigurationInfo configRequested = accountManager.getConfigurationInfo();

            String statusUrl                     = configRequested.getStatusUri();
            String storageUrl                    = configRequested.getStorageUri();
            String cloudUrl                      = configRequested.getCloudUri();
            byte[] unidentifiedAccessCaPublicKey = configRequested.getUnidentifiedDeliveryCaPublicKey();
            byte[] zkPublicKey                   = configRequested.getZkPublicKey();
            String supportEmail                  = configRequested.getSupportEmail();
            String fcmSenderId                   = configRequested.getFcmSenderId();
            String oldFcmSenderId                = SignalStore.serviceConfigurationValues().getFcmSenderId();

            if (
                            cloudUrl                      != null &&
                            statusUrl                     != null &&
                            storageUrl                    != null &&
                            unidentifiedAccessCaPublicKey != null &&
                            zkPublicKey                   != null &&
                            fcmSenderId                   != null) {

                SignalStore.serviceConfigurationValues().setCloudUrl(cloudUrl);
                SignalStore.serviceConfigurationValues().setCloud2Url(cloudUrl);
                SignalStore.serviceConfigurationValues().setStorageUrl(storageUrl);
                SignalStore.serviceConfigurationValues().setStatusUrl(statusUrl);
                SignalStore.serviceConfigurationValues().setUnidentifiedAccessCaPublicKey(unidentifiedAccessCaPublicKey);
                SignalStore.serviceConfigurationValues().setZkPublicKey(zkPublicKey);
                SignalStore.serviceConfigurationValues().setSupportEmail(supportEmail);

                if (!fcmSenderId.equals(oldFcmSenderId)) {
                    SignalStore.serviceConfigurationValues().setFcmSenderId(fcmSenderId);
                    ApplicationDependencies.getJobManager().add(new FcmRefreshJob());
                }

                Log.i(TAG, "Successfully updated service configuration");

            } else {
                Log.w(TAG, "Failed to update service configuration as one or more parameters received are null");
            }

            SignalStore.setLastServiceConfigRefreshTime(System.currentTimeMillis());

        } catch (IOException e) {
            Log.w(TAG, "IOException while trying to refresh service configuration. Skipping.");
        }
    }

    @Override
    public void onFailure() { }

    public static final class Factory implements Job.Factory<ServiceConfigRefreshJob> {

        @Override
        public @NonNull ServiceConfigRefreshJob create(@NonNull Parameters parameters, @NonNull Data data) {
            return new ServiceConfigRefreshJob(parameters);
        }
    }
}