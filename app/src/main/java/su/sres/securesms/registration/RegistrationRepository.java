package su.sres.securesms.registration;

import static su.sres.securesms.InitialActivity.TRUSTSTORE_FILE_NAME;
import static su.sres.securesms.jobs.CertificateRefreshJob.CERT_ALIASES;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import su.sres.core.util.logging.Log;

import org.greenrobot.eventbus.EventBus;
import org.signal.zkgroup.profiles.ProfileKey;

import su.sres.securesms.R;
import su.sres.securesms.crypto.IdentityKeyUtil;
import su.sres.securesms.crypto.PreKeyUtil;
import su.sres.securesms.crypto.ProfileKeyUtil;
import su.sres.securesms.crypto.SenderKeyUtil;
import su.sres.securesms.crypto.SessionUtil;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.IdentityDatabase;
import su.sres.securesms.database.RecipientDatabase;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.events.ServerCertErrorEvent;
import su.sres.securesms.jobmanager.JobManager;
import su.sres.securesms.jobs.CertificateRefreshJob;
import su.sres.securesms.jobs.DirectorySyncJob;
import su.sres.securesms.jobs.FcmRefreshJob;
import su.sres.securesms.jobs.LicenseManagementJob;
import su.sres.securesms.jobs.RotateCertificateJob;
import su.sres.securesms.jobs.StickerPackDownloadJob;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.securesms.push.AccountManagerFactory;
import su.sres.securesms.push.SignalServiceTrustStore;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.service.DirectoryRefreshListener;
import su.sres.securesms.service.RotateSignedPreKeyListener;
import su.sres.securesms.stickers.BlessedPacks;
import su.sres.securesms.util.TextSecurePreferences;

import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.util.KeyHelper;
import org.whispersystems.libsignal.util.guava.Optional;

import su.sres.signalservice.api.SignalServiceAccountManager;
import su.sres.signalservice.api.messages.calls.ConfigurationInfo;
import su.sres.signalservice.api.messages.calls.SystemCertificates;
import su.sres.signalservice.api.push.TrustStore;
import su.sres.signalservice.api.util.UuidUtil;
import su.sres.signalservice.internal.ServiceResponse;
import su.sres.signalservice.internal.push.VerifyAccountResponse;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.UUID;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Operations required for finalizing the registration of an account. This is
 * to be used after verifying the code and registration lock (if necessary) with
 * the server and being issued a UUID.
 */
public final class RegistrationRepository {

  private static final String TAG = Log.tag(RegistrationRepository.class);

  private final Application context;

  public RegistrationRepository(@NonNull Application context) {
    this.context = context;
  }

  public int getRegistrationId() {
    int registrationId = TextSecurePreferences.getLocalRegistrationId(context);
    if (registrationId == 0) {
      registrationId = KeyHelper.generateRegistrationId(false);
      TextSecurePreferences.setLocalRegistrationId(context, registrationId);
    }
    return registrationId;
  }

  public @NonNull ProfileKey getProfileKey(@NonNull String e164) {
    ProfileKey profileKey = findExistingProfileKey(context, e164);

    if (profileKey == null) {
      profileKey = ProfileKeyUtil.createNew();
      Log.i(TAG, "No profile key found, created a new one");
    }

    return profileKey;
  }

  public Single<ServiceResponse<VerifyAccountResponse>> registerAccountWithoutRegistrationLock(@NonNull RegistrationData registrationData,
                                                                                               @NonNull VerifyAccountResponse response)
  {
    return registerAccount(registrationData, response, null);
  }

  private Single<ServiceResponse<VerifyAccountResponse>> registerAccount(@NonNull RegistrationData registrationData,
                                                                         @NonNull VerifyAccountResponse response,
                                                                         @Nullable String pin)
  {
    return Single.<ServiceResponse<VerifyAccountResponse>>fromCallable(() -> {
      try {
        registerAccountInternal(registrationData, response, pin);

        JobManager jobManager = ApplicationDependencies.getJobManager();
        jobManager.add(new DirectorySyncJob(false));
        jobManager.add(new RotateCertificateJob());

        DirectoryRefreshListener.schedule(context);
        RotateSignedPreKeyListener.schedule(context);

        return ServiceResponse.forResult(response, 200, null);
      } catch (IOException e) {
        return ServiceResponse.forUnknownError(e);
      }
    }).subscribeOn(Schedulers.io());
  }

  @WorkerThread
  private void registerAccountInternal(@NonNull RegistrationData registrationData,
                                       @NonNull VerifyAccountResponse response,
                                       @Nullable String pin)
      throws IOException
  {
    SessionUtil.archiveAllSessions();
    SenderKeyUtil.clearAllState(context);

    UUID    uuid   = UuidUtil.parseOrThrow(response.getUuid());
    boolean hasPin = response.isStorageCapable();

    IdentityKeyPair    identityKey  = IdentityKeyUtil.getIdentityKeyPair(context);
    List<PreKeyRecord> records      = PreKeyUtil.generatePreKeys(context);
    SignedPreKeyRecord signedPreKey = PreKeyUtil.generateSignedPreKey(context, identityKey, true);

    SignalServiceAccountManager accountManager = AccountManagerFactory.createAuthenticated(context, uuid, registrationData.getE164(), registrationData.getPassword());
    accountManager.setPreKeys(identityKey.getPublicKey(), signedPreKey, records);

    if (registrationData.isFcm()) {
      accountManager.setGcmId(Optional.fromNullable(registrationData.getFcmToken()));
    }

    /// Shadow-specific start

    ConfigurationInfo configRequested = accountManager.getConfigurationInfo();

    String  statusUrl                     = configRequested.getStatusUri();
    String  storageUrl                    = configRequested.getStorageUri();
    String  cloudUrl                      = configRequested.getCloudUri();
    String  voipUrl                       = configRequested.getSfuUri();
    byte[]  unidentifiedAccessCaPublicKey = configRequested.getUnidentifiedDeliveryCaPublicKey();
    byte[]  zkPublicKey                   = configRequested.getZkPublicKey();
    String  supportEmail                  = configRequested.getSupportEmail();
    String  fcmSenderId                   = configRequested.getFcmSenderId();
    String  oldFcmSenderId                = SignalStore.serviceConfigurationValues().getFcmSenderId();
    boolean paymentsEnabled               = configRequested.getPaymentsEnabled();

    SystemCertificates systemCerts = accountManager.getSystemCerts();

    byte[] cloudCertABytes   = systemCerts.getCloudCertA();
    byte[] cloudCertBBytes   = systemCerts.getCloudCertB();
    byte[] storageCertABytes = systemCerts.getStorageCertA();
    byte[] storageCertBBytes = systemCerts.getStorageCertB();

    // if no cloud certificate at all is received from the server, registration will fail; same thing for storage, but as long as storage is not in place this is relaxed
    if (
        cloudUrl != null &&
        statusUrl != null &&
        storageUrl != null &&
        voipUrl != null &&
        unidentifiedAccessCaPublicKey != null &&
        zkPublicKey != null &&
        fcmSenderId != null &&
        ((cloudCertABytes != null) || (cloudCertBBytes != null))
      // && ((storageCertABytes != null) || (storageCertBBytes != null))
    )
    {
      SignalStore.serviceConfigurationValues().setCloudUrl(cloudUrl);
      SignalStore.serviceConfigurationValues().setCloud2Url(cloudUrl);
      SignalStore.serviceConfigurationValues().setStorageUrl(storageUrl);
      SignalStore.serviceConfigurationValues().setVoipUrl(voipUrl);
      SignalStore.serviceConfigurationValues().setStatusUrl(statusUrl);
      SignalStore.serviceConfigurationValues().setUnidentifiedAccessCaPublicKey(unidentifiedAccessCaPublicKey);
      SignalStore.serviceConfigurationValues().setZkPublicKey(zkPublicKey);
      SignalStore.serviceConfigurationValues().setSupportEmail(supportEmail);
      SignalStore.serviceConfigurationValues().setPaymentsEnabled(paymentsEnabled);

      // upon the initial registration the sender ID would have been already set from the QR code, but this is for subsequent re-registrations
      if (!fcmSenderId.equals(oldFcmSenderId)) {
        SignalStore.serviceConfigurationValues().setFcmSenderId(fcmSenderId);
        ApplicationDependencies.getJobManager().add(new FcmRefreshJob());
      }

      TrustStore trustStore          = new SignalServiceTrustStore(context);
      char[]     shadowStorePassword = trustStore.getKeyStorePassword().toCharArray();
      KeyStore   shadowStore;

      try (InputStream keyStoreInputStream = trustStore.getKeyStoreInputStream()) {
        shadowStore = KeyStore.getInstance("BKS");
        shadowStore.load(keyStoreInputStream, shadowStorePassword);

        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");

        // if no cert is present, just skip to the next one
        if (cloudCertABytes != null) {

          InputStream     cloudCertAInputStream = new ByteArrayInputStream(cloudCertABytes);
          X509Certificate cloudCertA            = (X509Certificate) certFactory.generateCertificate(cloudCertAInputStream);

          // at registration we don't allow expired certs, those will be caught by CertificateException below; not yet valid is OK
          try {
            cloudCertA.checkValidity();
          } catch (CertificateNotYetValidException e) {
            Log.i(TAG, "cloudCertA not yet valid, adding anyway");
          }

          shadowStore.setCertificateEntry(CERT_ALIASES[0], cloudCertA);
          Log.i(TAG, "cloudCertA added");
        } else {
          Log.i(TAG, "cloudCertA is missing. Skipping");
        }

        if (cloudCertBBytes != null) {

          InputStream     cloudCertBInputStream = new ByteArrayInputStream(cloudCertBBytes);
          X509Certificate cloudCertB            = (X509Certificate) certFactory.generateCertificate(cloudCertBInputStream);

          try {
            cloudCertB.checkValidity();
          } catch (CertificateNotYetValidException e) {
            Log.i(TAG, "cloudCertB not yet valid, adding anyway");
          }

          shadowStore.setCertificateEntry(CERT_ALIASES[1], cloudCertB);
          Log.i(TAG, "cloudCertB added");
        } else {
          Log.i(TAG, "cloudCertB is missing. Skipping");
        }

        if (storageCertABytes != null) {

          InputStream     storageCertAInputStream = new ByteArrayInputStream(storageCertABytes);
          X509Certificate storageCertA            = (X509Certificate) certFactory.generateCertificate(storageCertAInputStream);

          try {
            storageCertA.checkValidity();
          } catch (CertificateNotYetValidException e) {
            Log.i(TAG, "storageCertA not yet valid, adding anyway");
          }

          shadowStore.setCertificateEntry(CERT_ALIASES[4], storageCertA);
          Log.i(TAG, "storageCertA added");
        } else {
          Log.i(TAG, "storageCertA is missing. Skipping");
        }

        if (storageCertBBytes != null) {

          InputStream     storageCertBInputStream = new ByteArrayInputStream(storageCertBBytes);
          X509Certificate storageCertB            = (X509Certificate) certFactory.generateCertificate(storageCertBInputStream);

          try {
            storageCertB.checkValidity();
          } catch (CertificateNotYetValidException e) {
            Log.i(TAG, "storageCertB not yet valid, adding anyway");
          }

          shadowStore.setCertificateEntry(CERT_ALIASES[5], storageCertB);
          Log.i(TAG, "storageCertB added");
        } else {
          Log.i(TAG, "storageCertB is missing. Skipping");
        }

        keyStoreInputStream.close();

        try (FileOutputStream fos = context.openFileOutput(TRUSTSTORE_FILE_NAME, Context.MODE_PRIVATE)) {
          shadowStore.store(fos, shadowStorePassword);
        }

      } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException e) {
        Log.w(TAG, "Exception occurred while trying to import a certificate");
        EventBus.getDefault().post(new ServerCertErrorEvent(R.string.certificate_load_unsuccessful));
      }

    } else {
      EventBus.getDefault().post(new ServerCertErrorEvent(R.string.certificate_load_unsuccessful));
    }

    /// Shadow-specific end

    RecipientDatabase recipientDatabase = DatabaseFactory.getRecipientDatabase(context);
    RecipientId       selfId            = Recipient.externalPush(context, uuid, registrationData.getE164(), true).getId();

    recipientDatabase.setProfileSharing(selfId, true);
    recipientDatabase.markRegistered(selfId, uuid);

    TextSecurePreferences.setLocalNumber(context, registrationData.getE164());
    TextSecurePreferences.setLocalUuid(context, uuid);
    recipientDatabase.setProfileKey(selfId, registrationData.getProfileKey());
    ApplicationDependencies.getRecipientCache().clearSelf();

    TextSecurePreferences.setFcmToken(context, registrationData.getFcmToken());
    TextSecurePreferences.setFcmDisabled(context, registrationData.isNotFcm());
    TextSecurePreferences.setWebsocketRegistered(context, true);

    ApplicationDependencies.getIdentityStore()
                           .saveIdentityWithoutSideEffects(selfId,
                                                           identityKey.getPublicKey(),
                                                           IdentityDatabase.VerifiedStatus.VERIFIED,
                                                           true,
                                                           System.currentTimeMillis(),
                                                           true);

    TextSecurePreferences.setPushServerPassword(context, registrationData.getPassword());
    TextSecurePreferences.setPushRegistered(context, true);
    TextSecurePreferences.setSignedPreKeyRegistered(context, true);
    TextSecurePreferences.setPromptedPushRegistration(context, true);
    TextSecurePreferences.setUnauthorizedReceived(context, false);

    /// Shadow-specific

    CertificateRefreshJob.scheduleIfNecessary();
    LicenseManagementJob.scheduleIfNecessary();

    loadStickers(context);
  }

  @WorkerThread
  private static @Nullable ProfileKey findExistingProfileKey(@NonNull Context context, @NonNull String e164number) {
    RecipientDatabase     recipientDatabase = DatabaseFactory.getRecipientDatabase(context);
    Optional<RecipientId> recipient         = recipientDatabase.getByE164(e164number);

    if (recipient.isPresent()) {
      return ProfileKeyUtil.profileKeyOrNull(Recipient.resolved(recipient.get()).getProfileKey());
    }

    return null;
  }

  private void loadStickers(Context context) {

    if (!TextSecurePreferences.areStickersDownloaded(context)) {

      ApplicationDependencies.getJobManager().add(StickerPackDownloadJob.forInstall(BlessedPacks.ZOZO.getPackId(), BlessedPacks.ZOZO.getPackKey(), false));
      ApplicationDependencies.getJobManager().add(StickerPackDownloadJob.forInstall(BlessedPacks.BANDIT.getPackId(), BlessedPacks.BANDIT.getPackKey(), false));
      ApplicationDependencies.getJobManager().add(StickerPackDownloadJob.forInstall(BlessedPacks.DAY_BY_DAY.getPackId(), BlessedPacks.DAY_BY_DAY.getPackKey(), false));
      ApplicationDependencies.getJobManager().add(StickerPackDownloadJob.forReference(BlessedPacks.SWOON_HANDS.getPackId(), BlessedPacks.SWOON_HANDS.getPackKey()));
      ApplicationDependencies.getJobManager().add(StickerPackDownloadJob.forReference(BlessedPacks.SWOON_FACES.getPackId(), BlessedPacks.SWOON_FACES.getPackKey()));

      TextSecurePreferences.setStickersDownloaded(context, true);
    }
  }
}
