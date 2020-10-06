package su.sres.securesms.jobs;

import android.annotation.SuppressLint;
import android.provider.Settings.Secure;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import su.sres.securesms.activation.Feature;
import su.sres.securesms.activation.License;
import su.sres.securesms.activation.LicenseReader;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import su.sres.securesms.BuildConfig;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobmanager.impl.NetworkConstraint;
import su.sres.securesms.keyvalue.ServiceConfigurationValues;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.securesms.logging.Log;

import su.sres.securesms.util.Base64;
import su.sres.securesms.util.TextSecurePreferences;
import su.sres.signalservice.api.push.exceptions.PushNetworkException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LicenseManagementJob extends BaseJob {

    public static final String KEY = "LicenseManagementJob";

    private static final String TAG = LicenseManagementJob.class.getSimpleName();

    private static final long REFRESH_INTERVAL = TimeUnit.MINUTES.toMillis(360);

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
                               AbsentLicenseFileException,
                               InvalidLicenseFileException,
                               NullPsidException,
                               LicenseAllocationOrValidationException,
                               LicenseNoMoreValidException,
                               NoSuchAlgorithmException
    {
        Log.i(TAG, "LicenseManagementJob.onRun()");

        @SuppressLint("HardwareIds") String psid = calculatePsid(Secure.getString(context.getContentResolver(), Secure.ANDROID_ID));

        byte [] candidateBytes = config.retrieveLicense();

        if(candidateBytes == null) {

            // there's no license file locally, so suppose we are eligible for unconditional trial, let's check that
            switch(config.getTrialStatus()) {
                case 0:
                   int duration = requestTrial(psid);
                   if (duration != -1) {
                       Log.i(TAG, "Trial request success. Setting trial in progress. Setting licensed as true");
                       config.setTrialStatus(1);
                       config.setTrialStartTime(System.currentTimeMillis());
                       config.setTrialDuration(duration);
                       config.setLicensed(true);
                       SignalStore.setLastLicenseRefreshTime(System.currentTimeMillis());
                       return;
                   }
                   // this is when we are requesting a trial already used in the past, e.g. after a reinstall
                   else Log.i(TAG, "Trial request failure.");

                   break;

                case 1:
                    if ((System.currentTimeMillis() > (config.getTrialStartTime() + TimeUnit.DAYS.toMillis(config.getTrialDuration())) ) || !validateTrial(psid) ) {
                        Log.i(TAG, "Trial expired!");
                        config.setTrialStatus(2);
                        config.setLicensed(false);
                    } else {
                        Log.i(TAG, "Trial validation success. Continuing to use trial");
                        SignalStore.setLastLicenseRefreshTime(System.currentTimeMillis());
                        return;
                    }

                    break;

                case 2: break;
            }

            // as there's no license file locally, so download, validate and store it

            byte[] licenseBytes = downloadLicense();

            if (licenseBytes != null) {

                License license = License.Create.from(licenseBytes);

                LicenseStatus status = verifyLicenseFile(license);

                if(status == LicenseStatus.CORRUPTED || status == LicenseStatus.EXPIRED || status == LicenseStatus.TAMPERED) {
                    SignalStore.setLastLicenseRefreshTime(System.currentTimeMillis());
                    throw new InvalidLicenseFileException();
                    // here the job will fail by unretryable exception
                } else if(status == LicenseStatus.NYV) {
                    // store the license and exit, since it's not yet valid
                    config.storeLicense(licenseBytes);
                } else {
                    // the license is OK, store it, allocate it and and web-validate it
                    config.storeLicense(licenseBytes);
                    if (allocate(license, psid)) {
                        Log.i(TAG, "License allocation success");
                        if(validate(license, psid)) {
                            Log.i(TAG, "License validation success. Setting licensed as true");
                            config.setLicensed(true);
                        }
                    }
                }
            } else {
                Log.w(TAG, "Failed to retrieve the license file");
                SignalStore.setLastLicenseRefreshTime(System.currentTimeMillis());
                throw new AbsentLicenseFileException();
                // here the job will fail by unretryable exception
            }

        } else {
            // there's a license file locally already

            License license = new LicenseReader(new ByteArrayInputStream(candidateBytes)).read();
            LicenseStatus status = verifyLicenseFile(license);

            if (config.isLicensed()) {
                // we're licensed to the best of local knowledge, but need to validate. If invalid, throw a retryable exception
                if(status == LicenseStatus.OK && validate(license, psid)) {
                    Log.i(TAG, "License validation success. Exiting");
                } else {
                    Log.i(TAG, "License validation failure. Setting licensed as false and removing the license file.");
                    config.setLicensed(false);
                    config.removeLicense();
                    throw new LicenseNoMoreValidException(); // this is retryable, the client will download a new license (if available) on retry
                }

            } else {
                // We're not licensed. A valid case why that would be is that the license was not yet valid on previous check. But it might be now.
                if(status == LicenseStatus.OK) {

                    if(allocate(license, psid) && validate(license, psid)) {
                        Log.i(TAG, "License web-check success. Setting the app as licensed");
                        config.setLicensed(true);
                    }
                } else if(status != LicenseStatus.NYV) {
                    // this is some cornercase, like the file previously getting corrupted somehow
                    Log.i(TAG, "License validation failure. Removing the license file.");
                    config.removeLicense();
                }
                // if still not valid, we'd like to preserve the license file for future checks and do nothing
            }
        }

        SignalStore.setLastLicenseRefreshTime(System.currentTimeMillis());
    }

    @Override
    public boolean onShouldRetry(@NonNull Exception exception) {
        return (exception instanceof PushNetworkException) || (exception instanceof LicenseAllocationOrValidationException) || (exception instanceof LicenseNoMoreValidException);
    }

    public static void scheduleIfNecessary() {
        long timeSinceLastRefresh = System.currentTimeMillis() - SignalStore.getLastLicenseRefreshTime();

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
        SignalStore.setLastLicenseRefreshTime(System.currentTimeMillis());
    }

    private @Nullable byte [] downloadLicense() throws IOException {
        return ApplicationDependencies.getSignalServiceAccountManager().getLicense(TextSecurePreferences.getLocalNumber(context) + ".bin");
    }

    private LicenseStatus verifyLicenseFile(License license) {

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
        } else {
            return LicenseStatus.OK;
        }
    }

    private boolean featuresOK(License license) {
        Map<String, Feature> featureMap = license.getFeatures();
        @Nullable Feature assignee = featureMap.get("Assignee");
        @Nullable Feature validFrom = featureMap.get("Valid From");

        if (assignee == null || validFrom == null) {
            return false;
        } else {
            return assignee.getString() != null;
        }
    }

    private boolean allocate(License license, String psid) throws LicenseAllocationOrValidationException {

        String assignee = license.getFeatures().get("Assignee").getString().replaceAll(" ", "%20");
        String id = psid.replaceAll("/", "%2f");

        OkHttpClient client  = new OkHttpClient();
        Request request = new Request.Builder().url(String.format("%s/license/android/allocate/%s/%s/%s",
                                                                  BuildConfig.LICENSE_URL,
                                                                  license.getLicenseId().toString(),
                                                                  assignee,
                                                                  id))
                                               .build();

        try {
            Response response = client.newCall(request).execute();
            return response.code() == 200;
        } catch (IOException e) {
            Log.i(TAG, e);

            throw new LicenseAllocationOrValidationException();
            // here the job will fail by retryable exception
        }
    }

    private boolean validate(License license, String psid) throws LicenseAllocationOrValidationException {

        String id = psid.replaceAll("/", "%2f");

        OkHttpClient client  = new OkHttpClient();
        Request request = new Request.Builder().url(String.format("%s/license/android/check/%s/%s",
                                                                  BuildConfig.LICENSE_URL,
                                                                  license.getLicenseId().toString(),
                                                                  id))
                                               .build();

        try {
            Response response = client.newCall(request).execute();
            return response.code() == 200;
        } catch (IOException e) {
            throw new LicenseAllocationOrValidationException();
            // here the job will fail by retryable exception
        }
    }

    private int requestTrial(String psid) throws LicenseAllocationOrValidationException {

        String id = psid.replaceAll("/", "%2f");

        OkHttpClient client  = new OkHttpClient();
        Request request = new Request.Builder().url(String.format("%s/trial/android/request/%s",
                BuildConfig.LICENSE_URL,
                id))
                .build();

        try {
            Response response = client.newCall(request).execute();
            if (response.code() == 200) {
                if (response.body() != null) {                

                    return Integer.parseInt(response.body().string());

                } else throw new LicenseAllocationOrValidationException();

            } else return -1;

        } catch (IOException e) {
            Log.i(TAG, e);

            throw new LicenseAllocationOrValidationException();
            // here the job will fail by retryable exception
        }
    }

    private boolean validateTrial(String psid) throws LicenseAllocationOrValidationException {

        String id = psid.replaceAll("/", "%2f");

        OkHttpClient client  = new OkHttpClient();
        Request request = new Request.Builder().url(String.format("%s/trial/android/check/%s",
                BuildConfig.LICENSE_URL,
                id))
                .build();

        try {
            Response response = client.newCall(request).execute();
            return response.code() == 200;
        } catch (IOException e) {
            throw new LicenseAllocationOrValidationException();
            // here the job will fail by retryable exception
        }
    }

    public static String calculatePsid(String androidId) throws NoSuchAlgorithmException, NullPsidException {

        if(androidId != null) {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            return Base64.encodeBytes(messageDigest.digest(androidId.getBytes(StandardCharsets.UTF_8)));
        } else {
            throw new NullPsidException();
        }
    }

    public static final class Factory implements Job.Factory<LicenseManagementJob> {

        @Override
        public @NonNull
        LicenseManagementJob create(@NonNull Parameters parameters, @NonNull Data data) {

            return new LicenseManagementJob(parameters);
        }
    }

    private static class AbsentLicenseFileException extends Exception {}

    private static class InvalidLicenseFileException extends Exception {}

    private static class LicenseAllocationOrValidationException extends Exception {}

    public static class NullPsidException extends Exception {}

    private static class LicenseNoMoreValidException extends Exception {}

    private enum LicenseStatus {
        OK,
        TAMPERED,
        CORRUPTED,
        EXPIRED,
        NYV
    }
}