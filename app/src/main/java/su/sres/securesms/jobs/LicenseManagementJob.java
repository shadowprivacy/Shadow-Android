package su.sres.securesms.jobs;

import static su.sres.securesms.service.DirectoryRefreshListener.INTERVAL;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.iid.FirebaseInstanceId;

import su.sres.securesms.R;
import su.sres.securesms.activation.Feature;
import su.sres.securesms.activation.License;
import su.sres.securesms.activation.LicenseReader;
import su.sres.securesms.contacts.sync.DirectoryHelper;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.RecipientDatabase;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobmanager.impl.NetworkConstraint;
import su.sres.securesms.keyvalue.ServiceConfigurationValues;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.securesms.logging.Log;

import su.sres.securesms.util.Base64;
import su.sres.securesms.util.TextSecurePreferences;
import su.sres.signalservice.api.SignalServiceAccountManager;
import su.sres.signalservice.api.push.exceptions.AuthorizationFailedException;
import su.sres.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import su.sres.signalservice.api.push.exceptions.PushNetworkException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LicenseManagementJob extends BaseJob {

    public static final String KEY = "LicenseManagementJob";

    private static final String TAG = LicenseManagementJob.class.getSimpleName();

    private static final long REFRESH_INTERVAL = TimeUnit.MINUTES.toMillis(480);

    private final ServiceConfigurationValues config = SignalStore.serviceConfigurationValues();

    private static final byte[] pubKey = new byte[]{
            (byte) 0x52,
            (byte) 0x53, (byte) 0x41, (byte) 0x00, (byte) 0x30, (byte) 0x82, (byte) 0x01, (byte) 0x22, (byte) 0x30,
            (byte) 0x0D, (byte) 0x06, (byte) 0x09, (byte) 0x2A, (byte) 0x86, (byte) 0x48, (byte) 0x86, (byte) 0xF7,
            (byte) 0x0D, (byte) 0x01, (byte) 0x01, (byte) 0x01, (byte) 0x05, (byte) 0x00, (byte) 0x03, (byte) 0x82,
            (byte) 0x01, (byte) 0x0F, (byte) 0x00, (byte) 0x30, (byte) 0x82, (byte) 0x01, (byte) 0x0A, (byte) 0x02,
            (byte) 0x82, (byte) 0x01, (byte) 0x01, (byte) 0x00, (byte) 0x8C, (byte) 0x1C, (byte) 0xEB, (byte) 0xFE,
            (byte) 0x46, (byte) 0x87, (byte) 0x19, (byte) 0x99, (byte) 0x4B, (byte) 0x7F, (byte) 0x4C, (byte) 0x42,
            (byte) 0x17, (byte) 0x83, (byte) 0x36, (byte) 0x44, (byte) 0xAE, (byte) 0xDA, (byte) 0x73, (byte) 0xB0,
            (byte) 0x73, (byte) 0xA0, (byte) 0x0E, (byte) 0x81, (byte) 0x63, (byte) 0xBA, (byte) 0xFC, (byte) 0x08,
            (byte) 0xFC, (byte) 0x88, (byte) 0x5F, (byte) 0x42, (byte) 0x57, (byte) 0x70, (byte) 0xA1, (byte) 0xFF,
            (byte) 0x96, (byte) 0x72, (byte) 0xD3, (byte) 0xAA, (byte) 0x09, (byte) 0x2F, (byte) 0x7E, (byte) 0x6B,
            (byte) 0x4C, (byte) 0xA8, (byte) 0x37, (byte) 0xCB, (byte) 0x1A, (byte) 0xB6, (byte) 0x25, (byte) 0x1A,
            (byte) 0x61, (byte) 0x22, (byte) 0xC4, (byte) 0x39, (byte) 0xB6, (byte) 0x89, (byte) 0x65, (byte) 0x78,
            (byte) 0x25, (byte) 0x18, (byte) 0x3C, (byte) 0x83, (byte) 0x23, (byte) 0x73, (byte) 0x6C, (byte) 0x4C,
            (byte) 0xAF, (byte) 0x45, (byte) 0xF1, (byte) 0xFB, (byte) 0x49, (byte) 0x50, (byte) 0x87, (byte) 0xB8,
            (byte) 0xAE, (byte) 0x07, (byte) 0xBC, (byte) 0x2C, (byte) 0x2E, (byte) 0x5C, (byte) 0x1C, (byte) 0x20,
            (byte) 0x86, (byte) 0xD6, (byte) 0x1B, (byte) 0xB5, (byte) 0x41, (byte) 0x1F, (byte) 0xA5, (byte) 0x19,
            (byte) 0x87, (byte) 0x9A, (byte) 0x20, (byte) 0x51, (byte) 0x5F, (byte) 0x8A, (byte) 0xC5, (byte) 0x64,
            (byte) 0xEA, (byte) 0x1F, (byte) 0x55, (byte) 0x84, (byte) 0x2C, (byte) 0x0F, (byte) 0xAA, (byte) 0x2B,
            (byte) 0x66, (byte) 0xB1, (byte) 0xF1, (byte) 0x19, (byte) 0xCE, (byte) 0x6F, (byte) 0xBD, (byte) 0x0F,
            (byte) 0x75, (byte) 0xAD, (byte) 0x8E, (byte) 0x61, (byte) 0x4C, (byte) 0x95, (byte) 0x5A, (byte) 0x54,
            (byte) 0xFD, (byte) 0x92, (byte) 0x33, (byte) 0xDF, (byte) 0xE5, (byte) 0xDD, (byte) 0x41, (byte) 0xAC,
            (byte) 0x63, (byte) 0xB2, (byte) 0x49, (byte) 0xA1, (byte) 0xE7, (byte) 0x82, (byte) 0xC8, (byte) 0x7C,
            (byte) 0x13, (byte) 0x49, (byte) 0xBF, (byte) 0xC0, (byte) 0xB3, (byte) 0x3D, (byte) 0xD3, (byte) 0x1F,
            (byte) 0x01, (byte) 0x84, (byte) 0x9E, (byte) 0xC4, (byte) 0x40, (byte) 0x36, (byte) 0x7E, (byte) 0x48,
            (byte) 0x9B, (byte) 0xA9, (byte) 0x0B, (byte) 0x73, (byte) 0x56, (byte) 0x24, (byte) 0x7E, (byte) 0x09,
            (byte) 0x5E, (byte) 0x12, (byte) 0xEB, (byte) 0x07, (byte) 0x7E, (byte) 0xDE, (byte) 0x3C, (byte) 0xC3,
            (byte) 0xE8, (byte) 0xA7, (byte) 0x80, (byte) 0x7E, (byte) 0xCD, (byte) 0x27, (byte) 0xCB, (byte) 0xB7,
            (byte) 0xF2, (byte) 0xF3, (byte) 0xC5, (byte) 0xA3, (byte) 0x11, (byte) 0xA0, (byte) 0x3D, (byte) 0xEE,
            (byte) 0xAB, (byte) 0xB1, (byte) 0xC0, (byte) 0x92, (byte) 0xC3, (byte) 0x1B, (byte) 0xC9, (byte) 0xA2,
            (byte) 0xF7, (byte) 0x51, (byte) 0xB8, (byte) 0x66, (byte) 0x64, (byte) 0x5D, (byte) 0x74, (byte) 0x4D,
            (byte) 0x94, (byte) 0xA1, (byte) 0x9A, (byte) 0x43, (byte) 0x92, (byte) 0x38, (byte) 0xDB, (byte) 0xF8,
            (byte) 0x55, (byte) 0x56, (byte) 0x5B, (byte) 0xB9, (byte) 0x25, (byte) 0xA0, (byte) 0xD7, (byte) 0xE4,
            (byte) 0xCB, (byte) 0xCF, (byte) 0xB1, (byte) 0x47, (byte) 0xA7, (byte) 0x39, (byte) 0x74, (byte) 0x21,
            (byte) 0xDE, (byte) 0x9E, (byte) 0xC6, (byte) 0xCE, (byte) 0x0C, (byte) 0x95, (byte) 0xED, (byte) 0x52,
            (byte) 0xE8, (byte) 0xAD, (byte) 0xFB, (byte) 0x8C, (byte) 0xCC, (byte) 0xF6, (byte) 0x4E, (byte) 0x44,
            (byte) 0xF4, (byte) 0xE4, (byte) 0x6F, (byte) 0x5B, (byte) 0x6E, (byte) 0xDB, (byte) 0x84, (byte) 0xF8,
            (byte) 0x11, (byte) 0x56, (byte) 0x7A, (byte) 0x75, (byte) 0x02, (byte) 0x03, (byte) 0x01, (byte) 0x00,
            (byte) 0x01
    };

    private static final int SUCCESS = 0;
    private static final int NETWORK_ERROR = 1;
    private static final int ERROR = 2;

    public LicenseManagementJob() {
        this(new Job.Parameters.Builder()
                .addConstraint(NetworkConstraint.KEY)
                .setMaxAttempts(10)
                .setMaxInstances(1)
                .build());
    }

    private LicenseManagementJob(@NonNull Job.Parameters parameters) {
        super(parameters);
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
    public void onRun() throws IOException,
            InvalidLicenseFileException,
            NoSuchAlgorithmException {

        Log.i(TAG, "LicenseManagementJob.onRun()");

        byte[] licenseBytes = null;

        try {
            licenseBytes = downloadLicense();
            // if the server is inaccessible with PushNetworkException, the job will retry
        } catch (NonSuccessfulResponseCodeException e) {
            // if the server responds with something else than 2xx, we assume that there is no file on the server and proceed to volume check based on no-license case
            // PushServiceSocket will throw the details in
            Log.w(TAG, e);
            config.setLicensed(false);
            config.removeLicense();
        }

        if (licenseBytes != null) {

            License license = License.Create.from(licenseBytes);
            LicenseStatus status = verifyLicenseFile(license);

            if (status == LicenseStatus.CORRUPTED || status == LicenseStatus.EXPIRED || status == LicenseStatus.TAMPERED || status == LicenseStatus.IRRELEVANT || status == LicenseStatus.NYV) {
                Log.i(TAG, "License verification failure. Setting licensed as false and removing the license file.");
                // the fresh info received from the server overrides the local keyvalues, if any
                config.setLicensed(false);
                config.removeLicense();

                // fail the job with unretryable exception and proceed to volume validation
                SignalStore.misc().setLastLicenseRefreshTime(System.currentTimeMillis());
                new VolumeValidationTask(context, config).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                throw new InvalidLicenseFileException();
            } else {
                // the license is OK, store it and set licensed as true
                config.storeLicense(licenseBytes);
                Log.i(TAG, "License verification success. Setting licensed as true");
                config.setLicensed(true);
            }
        } else {
            Log.w(TAG, "Failed to retrieve the license file");
            SignalStore.misc().setLastLicenseRefreshTime(System.currentTimeMillis());
            // if we can't download the license, continue to the volume validation stage using the locally stored keyvalues
        }

        new VolumeValidationTask(context, config).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

        SignalStore.misc().setLastLicenseRefreshTime(System.currentTimeMillis());
    }

    @Override
    public boolean onShouldRetry(@NonNull Exception exception) {
        return (exception instanceof PushNetworkException);
    }

    public static void scheduleIfNecessary() {
        long timeSinceLastRefresh = System.currentTimeMillis() - SignalStore.misc().getLastLicenseRefreshTime();

        if (timeSinceLastRefresh > REFRESH_INTERVAL) {
            Log.i(TAG, "Scheduling a license refresh. Time since last schedule: " + timeSinceLastRefresh + " ms");
            ApplicationDependencies.getJobManager().add(new LicenseManagementJob());
        } else {
            Log.i(TAG, "Holdtime not expired. Skipping.");
        }
    }

    @Override
    public void onFailure() {
        Log.w(TAG, "License management cycle failed!");
        SignalStore.misc().setLastLicenseRefreshTime(System.currentTimeMillis());
    }

    private @Nullable
    byte[] downloadLicense() throws IOException {
        return ApplicationDependencies.getSignalServiceAccountManager().getLicense();
    }

    private LicenseStatus verifyLicenseFile(License license) throws NoSuchAlgorithmException {

        if (!license.isOK(pubKey)) {
            Log.w(TAG, "The retrieved license has been tampered with!");
            return LicenseStatus.TAMPERED;
        } else if (!featuresOK(license)) {
            Log.w(TAG, "The retrieved license is corrupted!");
            return LicenseStatus.CORRUPTED;
        } else if (license.isExpired()) {
            Log.w(TAG, "The retrieved license is expired!");
            return LicenseStatus.EXPIRED;
        } else if (license.getFeatures().get("Valid From").getLong() > System.currentTimeMillis()) {
            Log.w(TAG, "The retrieved license is not yet valid!");
            return LicenseStatus.NYV;
        } else if (!isHashValid(license.getFeatures().get("Shared").getString(), extractDomain(config.getShadowUrl()))) {
            Log.w(TAG, "The retrieved license is irrelevant!");
            return LicenseStatus.IRRELEVANT;
        } else {
            return LicenseStatus.OK;
        }
    }

    private boolean featuresOK(License license) {
        Map<String, Feature> featureMap = license.getFeatures();
        @Nullable Feature assignee = featureMap.get("Assignee");
        @Nullable Feature validFrom = featureMap.get("Valid From");
        @Nullable Feature shared = featureMap.get("Shared");
        @Nullable Feature volumes = featureMap.get("Volumes");

        if (assignee == null || validFrom == null || shared == null || volumes == null) {
            return false;
        } else {
            return assignee.getString() != null;
        }
    }

    private static boolean isHashValid(String hash, String domain) throws NoSuchAlgorithmException {

        return calculateHash(domain).equals(hash);
    }

    public static String calculateHash(String domain) throws NoSuchAlgorithmException {
        MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
        return Base64.encodeBytes(messageDigest.digest(domain.getBytes(StandardCharsets.UTF_8)));
    }

    private static String extractDomain(String url) {
        int start = url.indexOf("https://");
        String tmpName, name = "";
        if (start >= 0) {
            tmpName = url.substring(start + 8);
            int end = tmpName.indexOf(":");
            if (end > 0) {
                name = tmpName.substring(0, end);
            } else {
                name = tmpName;
            }
        }

        return name;
    }

    private static void notifyUser(Context context, int message) {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context, message, Toast.LENGTH_LONG).show());
    }

    private static class SelfKickOffTask extends AsyncTask<Void, Void, Integer> {

        private final WeakReference<Context> contextReference;

        SelfKickOffTask(Context context) {
            contextReference = new WeakReference<>(context);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            Log.w(TAG, "Oversubscription detected. Self-unregistering.");
        }

        @Override
        protected void onPostExecute(Integer result) {
            super.onPostExecute(result);
            final Context context = contextReference.get();
            if (context != null) {
                switch (result) {
                    case NETWORK_ERROR:
                        notifyUser(context, R.string.LicenseManagementJob_error_connecting_to_server);
                        break;
                    case SUCCESS:

                        TextSecurePreferences.setPushRegistered(context, false);
                        SignalStore.registrationValues().clearRegistrationComplete();
                        notifyUser(context, R.string.LicenseManagementJob_self_unregistered);
                        break;
                }
            }
        }

        @Override
        protected Integer doInBackground(Void... params) {
            final Context context = contextReference.get();
            if (context != null) {
                try {
                    SignalServiceAccountManager accountManager = ApplicationDependencies.getSignalServiceAccountManager();

                    try {
                        accountManager.selfUnregister();
                    } catch (AuthorizationFailedException e) {
                        Log.w(TAG, e);
                    }

                    if (!TextSecurePreferences.isFcmDisabled(context)) {
                        FirebaseInstanceId.getInstance().deleteInstanceId();
                    }

                    return SUCCESS;
                } catch (IOException ioe) {
                    Log.w(TAG, ioe);
                    return NETWORK_ERROR;
                }
            } else return ERROR;
        }
    }

    private static class VolumeValidationTask extends AsyncTask<Void, Void, Integer> {
        private final WeakReference<Context> contextReference;
        private final ServiceConfigurationValues config;

        VolumeValidationTask(Context context, ServiceConfigurationValues config) {
            contextReference = new WeakReference<>(context);
            this.config = config;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            Log.i(TAG, "Retrieving the current directory.");
        }

        @Override
        protected void onPostExecute(Integer result) {
            super.onPostExecute(result);
            final Context context = contextReference.get();
            if (context != null) {
                switch (result) {
                    case NETWORK_ERROR:
                        notifyUser(context, R.string.LicenseManagementJob_error_connecting_to_server);
                        break;
                    case SUCCESS:
                        // this is to adjust the general directory refresh cycle
                        TextSecurePreferences.setDirectoryRefreshTime(context, System.currentTimeMillis() + INTERVAL);
                        Log.i(TAG, "Directory successfully retrieved. Proceeding to volume validation.");
                        validateVolumes(context, config);
                        break;
                }
            }
        }

        @Override
        protected Integer doInBackground(Void... params) {
            final Context context = contextReference.get();
            if (context != null) {
                try {
                    DirectoryHelper.refreshDirectory(context);
                    return SUCCESS;
                } catch (IOException ioe) {
                    Log.w(TAG, ioe);
                    return NETWORK_ERROR;
                }
            } else return ERROR;
        }

        private void validateVolumes(Context context, ServiceConfigurationValues config) {
            RecipientDatabase rdb = DatabaseFactory.getRecipientDatabase(context);
            int actualUsers = rdb.getRegistered().size();

            if (!config.isLicensed()) {
                // enforce free volumes
                if (actualUsers > 3)
                    new SelfKickOffTask(context).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

            } else {

                @Nullable byte[] licenseBytes = config.retrieveLicense();

                // the thing that should not be
                if (licenseBytes == null) {
                    if (actualUsers > 3)
                        new SelfKickOffTask(context).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

                } else {
                    // enforce volumes
                    try {
                        License license = new LicenseReader(new ByteArrayInputStream(licenseBytes)).read();
                        String[] volumes = license.getFeatures().get("Volumes").getString().split(":");
                        int licensedUsers = Integer.valueOf(volumes[1]);

                        if (licensedUsers < actualUsers)
                            new SelfKickOffTask(context).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                    } catch (IOException e) {
                        // noop
                    }
                }
            }
        }
    }

    public static final class Factory implements Job.Factory<LicenseManagementJob> {

        @Override
        public @NonNull
        LicenseManagementJob create(@NonNull Parameters parameters, @NonNull Data data) {

            return new LicenseManagementJob(parameters);
        }
    }

    private static class InvalidLicenseFileException extends Exception {
    }

    private enum LicenseStatus {
        OK,
        TAMPERED,
        CORRUPTED,
        EXPIRED,
        NYV,
        IRRELEVANT
    }
}