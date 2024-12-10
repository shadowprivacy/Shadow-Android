/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 * <p>
 * Licensed according to the LICENSE file in this repository.
 */

package su.sres.signalservice.api;

import com.google.protobuf.ByteString;

import su.sres.signalservice.api.payments.CurrencyConversions;
import su.sres.signalservice.api.profiles.ProfileAndCredential;
import su.sres.signalservice.api.push.ACI;
import su.sres.signalservice.api.storage.protos.DirectoryResponse;
import su.sres.signalservice.api.groupsv2.ClientZkOperations;
import su.sres.signalservice.api.groupsv2.GroupsV2Api;
import su.sres.signalservice.api.groupsv2.GroupsV2Operations;
import su.sres.signalservice.api.messages.calls.SystemCertificates;

import org.signal.zkgroup.profiles.ProfileKey;
import org.signal.zkgroup.profiles.ProfileKeyCredential;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.logging.Log;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.util.guava.Optional;

import su.sres.signalservice.api.crypto.ProfileCipher;
import su.sres.signalservice.api.crypto.ProfileCipherOutputStream;
import su.sres.signalservice.api.messages.calls.SystemCertificatesVersion;
import su.sres.signalservice.api.push.exceptions.NoContentException;
import su.sres.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import su.sres.signalservice.api.push.exceptions.PushNetworkException;
import su.sres.signalservice.api.storage.StorageId;
import su.sres.signalservice.api.storage.StorageKey;
import su.sres.signalservice.api.messages.calls.ConfigurationInfo;
import su.sres.signalservice.api.messages.calls.TurnServerInfo;
import su.sres.signalservice.api.messages.multidevice.DeviceInfo;
import su.sres.signalservice.api.profiles.SignalServiceProfileWrite;
import su.sres.signalservice.api.push.ContactTokenDetails;
import su.sres.signalservice.api.push.SignedPreKeyEntity;
import su.sres.signalservice.api.push.exceptions.NotFoundException;
import su.sres.signalservice.api.storage.SignalStorageCipher;
import su.sres.signalservice.api.storage.SignalStorageModels;
import su.sres.signalservice.api.storage.SignalStorageRecord;
import su.sres.signalservice.api.storage.StorageManifestKey;
import su.sres.signalservice.api.storage.SignalStorageManifest;
import su.sres.signalservice.api.util.CredentialsProvider;
import su.sres.signalservice.api.util.StreamDetails;
import su.sres.signalservice.internal.ServiceResponse;
import su.sres.signalservice.internal.configuration.SignalServiceConfiguration;
import su.sres.signalservice.internal.crypto.ProvisioningCipher;
import su.sres.signalservice.api.account.AccountAttributes;
import su.sres.signalservice.internal.push.AttachmentV2UploadAttributes;
import su.sres.signalservice.internal.push.AuthCredentials;
import su.sres.signalservice.internal.push.ProfileAvatarData;
import su.sres.signalservice.internal.push.PushServiceSocket;
import su.sres.signalservice.internal.push.RemoteConfigResponse;
import su.sres.signalservice.internal.push.RequestVerificationCodeResponse;
import su.sres.signalservice.internal.push.SignalServiceProtos;
import su.sres.signalservice.internal.push.VerifyAccountResponse;
import su.sres.signalservice.internal.push.WhoAmIResponse;
import su.sres.signalservice.internal.push.http.ProfileCipherOutputStreamFactory;
import su.sres.signalservice.internal.storage.protos.ManifestRecord;
import su.sres.signalservice.internal.storage.protos.ReadOperation;
import su.sres.signalservice.internal.storage.protos.StorageItem;
import su.sres.signalservice.internal.storage.protos.StorageItems;
import su.sres.signalservice.internal.storage.protos.StorageManifest;
import su.sres.signalservice.internal.storage.protos.WriteOperation;
import su.sres.signalservice.internal.util.StaticCredentialsProvider;
import su.sres.signalservice.internal.util.Util;

import su.sres.util.Base64;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static su.sres.signalservice.internal.push.ProvisioningProtos.ProvisionMessage;
import static su.sres.signalservice.internal.push.ProvisioningProtos.ProvisioningVersion;

/**
 * The main interface for creating, registering, and
 * managing a Signal Service account.
 *
 * @author Moxie Marlinspike
 */
public class SignalServiceAccountManager {

  private static final String TAG = SignalServiceAccountManager.class.getSimpleName();

  private final PushServiceSocket          pushServiceSocket;
  private final CredentialsProvider        credentials;
  private final String                     userAgent;
  private final GroupsV2Operations         groupsV2Operations;
  private final SignalServiceConfiguration configuration;

  /**
   * Construct a SignalServiceAccountManager.
   *
   * @param configuration The URL for the Shadow Service.
   * @param aci The Shadow Service UUID.
   * @param userLogin     The Shadow Service user login.
   * @param password      A Shadow Service password.
   * @param signalAgent   A string which identifies the client software.
   */
  public SignalServiceAccountManager(SignalServiceConfiguration configuration,
                                     ACI aci,
                                     String userLogin,
                                     String password,
                                     String signalAgent,
                                     boolean automaticNetworkRetry)
  {
    this(configuration,
         new StaticCredentialsProvider(aci, userLogin, password),
         signalAgent,
         new GroupsV2Operations(ClientZkOperations.create(configuration)),
         automaticNetworkRetry);
  }

  public SignalServiceAccountManager(SignalServiceConfiguration configuration,
                                     CredentialsProvider credentialsProvider,
                                     String signalAgent,
                                     GroupsV2Operations groupsV2Operations,
                                     boolean automaticNetworkRetry)
  {
    this.groupsV2Operations = groupsV2Operations;
    this.pushServiceSocket  = new PushServiceSocket(configuration, credentialsProvider, signalAgent, groupsV2Operations.getProfileOperations(), automaticNetworkRetry);
    this.credentials        = credentialsProvider;
    this.userAgent          = signalAgent;
    this.configuration      = configuration;
  }

  public byte[] getSenderCertificate() throws IOException {
    return this.pushServiceSocket.getSenderCertificate();
  }

  public byte[] getSenderCertificateForUserLoginPrivacy() throws IOException {
    return this.pushServiceSocket.getUuidOnlySenderCertificate();
  }

  public void setPin(Optional<String> pin) throws IOException {
    if (pin.isPresent()) {
      this.pushServiceSocket.setPin(pin.get());
    } else {
      this.pushServiceSocket.removeRegistrationLockV1();
    }
  }

  public ACI getOwnAci() throws IOException {
    return this.pushServiceSocket.getOwnAci();
  }

  public WhoAmIResponse getWhoAmI() throws IOException {
    return this.pushServiceSocket.getWhoAmI();
  }

  /**
   * Register/Unregister a Google Cloud Messaging registration ID.
   *
   * @param gcmRegistrationId The GCM id to register.  A call with an absent value will unregister.
   * @throws IOException
   */
  public void setGcmId(Optional<String> gcmRegistrationId) throws IOException {
    if (gcmRegistrationId.isPresent()) {
      this.pushServiceSocket.registerGcmId(gcmRegistrationId.get());
    } else {
      this.pushServiceSocket.unregisterGcmId();
    }
  }

  /**
   * Request a push challenge. A number will be pushed to the GCM (FCM) id. This can then be used
   * during SMS/call requests to bypass the CAPTCHA.
   *
   * @param gcmRegistrationId The GCM (FCM) id to use.
   * @param userLogin         The number to associate it with.
   * @throws IOException
   */
  public void requestRegistrationPushChallenge(String gcmRegistrationId, String userLogin) throws IOException {
    this.pushServiceSocket.requestPushChallenge(gcmRegistrationId, userLogin);
  }

  /**
   * Request an SMS verification code.  On success, the server will send
   * an SMS verification code to this Signal user.
   *
   * @param androidSmsRetrieverSupported
   * @param captchaToken                 If the user has done a CAPTCHA, include this.
   * @param challenge                    If present, it can bypass the CAPTCHA.
   */
  public ServiceResponse<RequestVerificationCodeResponse> requestSmsVerificationCode(boolean androidSmsRetrieverSupported, Optional<String> captchaToken, Optional<String> challenge, Optional<String> fcmToken) {
    try {
      this.pushServiceSocket.requestSmsVerificationCode(androidSmsRetrieverSupported, captchaToken, challenge);
      return ServiceResponse.forResult(new RequestVerificationCodeResponse(fcmToken), 200, null);
    } catch (IOException e) {
      return ServiceResponse.forUnknownError(e);
    }
  }

  /**
   * Verify a Signal Service account with a received SMS or voice verification code.
   *
   * @param verificationCode             The verification code received from admin
   *                                     (see {@link #requestSmsVerificationCode}.
   * @param signalProtocolRegistrationId A random 14-bit number that identifies this Signal install.
   *                                     This value should remain consistent across registrations for the
   *                                     same install, but probabilistically differ across registrations
   *                                     for separate installs.
   * @return The UUID of the user that was registered.
   * @throws IOException for various HTTP and networking errors
   */
  public ServiceResponse<VerifyAccountResponse> verifyAccount(String verificationCode,
                                                              int signalProtocolRegistrationId,
                                                              boolean fetchesMessages,
                                                              byte[] unidentifiedAccessKey,
                                                              boolean unrestrictedUnidentifiedAccess,
                                                              AccountAttributes.Capabilities capabilities,
                                                              boolean discoverableByUserLogin)
  {
    try {
      VerifyAccountResponse response = this.pushServiceSocket.verifyAccountCode(verificationCode,
                                                                                null,
                                                                                signalProtocolRegistrationId,
                                                                                fetchesMessages,
                                                                                null,
                                                                                unidentifiedAccessKey,
                                                                                unrestrictedUnidentifiedAccess,
                                                                                capabilities,
                                                                                discoverableByUserLogin);
      return ServiceResponse.forResult(response, 200, null);
    } catch (IOException e) {
      return ServiceResponse.forUnknownError(e);
    }
  }

  /**
   * Verify a Signal Service account with a received verification code with
   * registration lock.
   *
   * @param verificationCode             The verification code received from admin
   *                                     (see {@link #requestSmsVerificationCode}.
   * @param signalProtocolRegistrationId A random 14-bit number that identifies this Shadow install.
   *                                     This value should remain consistent across registrations for the
   *                                     same install, but probabilistically differ across registrations
   *                                     for separate installs.
   * @return The UUID of the user that was registered.
   */
    /* public ServiceResponse<VerifyAccountResponse> verifyAccountWithRegistrationLockPin(String verificationCode,
                                                                                       int signalProtocolRegistrationId,
                                                                                       boolean fetchesMessages,
                                                                                       String registrationLock,
                                                                                       byte[] unidentifiedAccessKey,
                                                                                       boolean unrestrictedUnidentifiedAccess,
                                                                                       AccountAttributes.Capabilities capabilities,
                                                                                       boolean discoverableByUserLogin)
    {
        try {
            VerifyAccountResponse response = this.pushServiceSocket.verifyAccountCode(verificationCode,
                                                                                      null,
                                                                                      signalProtocolRegistrationId,
                                                                                      fetchesMessages,
                                                                                      null,
                                                                                      unidentifiedAccessKey,
                                                                                      unrestrictedUnidentifiedAccess,
                                                                                      capabilities,
                                                                                      discoverableByUserLogin);
            return ServiceResponse.forResult(response, 200, null);
        } catch (IOException e) {
            return ServiceResponse.forUnknownError(e);
        }
    } */
  public ServiceResponse<VerifyAccountResponse> changeUserLogin(String code, String newLogin) {
    try {
      VerifyAccountResponse response = this.pushServiceSocket.changeUserLogin(code, newLogin);
      return ServiceResponse.forResult(response, 200, null);
    } catch (IOException e) {
      return ServiceResponse.forUnknownError(e);
    }
  }

  /**
   * Refresh account attributes with server.
   *
   * @param signalingKey                 52 random bytes.  A 32 byte AES key and a 20 byte Hmac256 key, concatenated.
   * @param signalProtocolRegistrationId A random 14-bit number that identifies this Signal install.
   *                                     This value should remain consistent across registrations for the same
   *                                     install, but probabilistically differ across registrations for
   *                                     separate installs.
   * @throws IOException
   */
  public void setAccountAttributes(String signalingKey, int signalProtocolRegistrationId, boolean fetchesMessages, String pin,
                                   byte[] unidentifiedAccessKey, boolean unrestrictedUnidentifiedAccess,
                                   AccountAttributes.Capabilities capabilities,
                                   boolean discoverableByUserLogin)
      throws IOException
  {
    this.pushServiceSocket.setAccountAttributes(signalingKey, signalProtocolRegistrationId, fetchesMessages, pin,
                                                unidentifiedAccessKey, unrestrictedUnidentifiedAccess,
                                                capabilities,
                                                discoverableByUserLogin);
  }

  /**
   * Register an identity key, signed prekey, and list of one time prekeys
   * with the server.
   *
   * @param identityKey    The client's long-term identity keypair.
   * @param signedPreKey   The client's signed prekey.
   * @param oneTimePreKeys The client's list of one-time prekeys.
   * @throws IOException
   */
  public void setPreKeys(IdentityKey identityKey, SignedPreKeyRecord signedPreKey, List<PreKeyRecord> oneTimePreKeys)
      throws IOException
  {
    this.pushServiceSocket.registerPreKeys(identityKey, signedPreKey, oneTimePreKeys);
  }

  /**
   * @return The server's count of currently available (eg. unused) prekeys for this user.
   * @throws IOException
   */
  public int getPreKeysCount() throws IOException {
    return this.pushServiceSocket.getAvailablePreKeys();
  }

  /**
   * Set the client's signed prekey.
   *
   * @param signedPreKey The client's new signed prekey.
   * @throws IOException
   */
  public void setSignedPreKey(SignedPreKeyRecord signedPreKey) throws IOException {
    this.pushServiceSocket.setCurrentSignedPreKey(signedPreKey);
  }

  /**
   * @return The server's view of the client's current signed prekey.
   * @throws IOException
   */
  public SignedPreKeyEntity getSignedPreKey() throws IOException {
    return this.pushServiceSocket.getCurrentSignedPreKey();
  }

  public DirectoryResponse getDirectoryResponse(long directoryVersion, boolean forceFull) throws IOException {

    return this.pushServiceSocket.getDirectoryResponse(directoryVersion, forceFull);
  }

  public byte[] getLicense() throws IOException {

    return this.pushServiceSocket.getLicense();
  }

  public Optional<SignalStorageManifest> getStorageManifest(StorageKey storageKey) throws IOException {
    try {
      String          authToken       = this.pushServiceSocket.getStorageAuth();
      StorageManifest storageManifest = this.pushServiceSocket.getStorageManifest(authToken);

      return Optional.of(SignalStorageModels.remoteToLocalStorageManifest(storageManifest, storageKey));
    } catch (InvalidKeyException | NotFoundException e) {
      Log.w(TAG, "Error while fetching manifest.", e);
      return Optional.absent();
    }
  }

  public long getStorageManifestVersion() throws IOException {
    try {
      String          authToken       = this.pushServiceSocket.getStorageAuth();
      StorageManifest storageManifest = this.pushServiceSocket.getStorageManifest(authToken);

      return storageManifest.getVersion();
    } catch (NotFoundException e) {
      return 0;
    }
  }

  public Optional<SignalStorageManifest> getStorageManifestIfDifferentVersion(StorageKey storageKey, long manifestVersion) throws IOException, InvalidKeyException {
    try {
      String          authToken       = this.pushServiceSocket.getStorageAuth();
      StorageManifest storageManifest = this.pushServiceSocket.getStorageManifestIfDifferentVersion(authToken, manifestVersion);

      if (storageManifest.getValue().isEmpty()) {
        Log.w(TAG, "Got an empty storage manifest!");
        return Optional.absent();
      }

      return Optional.of(SignalStorageModels.remoteToLocalStorageManifest(storageManifest, storageKey));
    } catch (NoContentException e) {
      return Optional.absent();
    }
  }

  public List<SignalStorageRecord> readStorageRecords(StorageKey storageKey, List<StorageId> storageKeys) throws IOException, InvalidKeyException {
    if (storageKeys.isEmpty()) {
      return Collections.emptyList();
    }

    List<SignalStorageRecord> result    = new ArrayList<>();
    ReadOperation.Builder     operation = ReadOperation.newBuilder();
    Map<ByteString, Integer>  typeMap   = new HashMap<>();

    for (StorageId key : storageKeys) {
      typeMap.put(ByteString.copyFrom(key.getRaw()), key.getType());

      if (StorageId.isKnownType(key.getType())) {
        operation.addReadKey(ByteString.copyFrom(key.getRaw()));
      } else {
        result.add(SignalStorageRecord.forUnknown(key));
      }
    }

    String       authToken = this.pushServiceSocket.getStorageAuth();
    StorageItems items     = this.pushServiceSocket.readStorageItems(authToken, operation.build());

    for (StorageItem item : items.getItemsList()) {
      Integer type = typeMap.get(item.getKey());
      if (type != null) {
        result.add(SignalStorageModels.remoteToLocalStorageRecord(item, type, storageKey));
      } else {
        Log.w(TAG, "No type found! Skipping.");
      }
    }

    return result;
  }

  /**
   * @return If there was a conflict, the latest {@link SignalStorageManifest}. Otherwise absent.
   */
  public Optional<SignalStorageManifest> resetStorageRecords(StorageKey storageKey,
                                                             SignalStorageManifest manifest,
                                                             List<SignalStorageRecord> allRecords)
      throws IOException, InvalidKeyException
  {
    return writeStorageRecords(storageKey, manifest, allRecords, Collections.<byte[]>emptyList(), true);
  }

  /**
   * @return If there was a conflict, the latest {@link SignalStorageManifest}. Otherwise absent.
   */
  public Optional<SignalStorageManifest> writeStorageRecords(StorageKey storageKey,
                                                             SignalStorageManifest manifest,
                                                             List<SignalStorageRecord> inserts,
                                                             List<byte[]> deletes)
      throws IOException, InvalidKeyException
  {
    return writeStorageRecords(storageKey, manifest, inserts, deletes, false);
  }

  /**
   * @return If there was a conflict, the latest {@link SignalStorageManifest}. Otherwise absent.
   */
  private Optional<SignalStorageManifest> writeStorageRecords(StorageKey storageKey,
                                                              SignalStorageManifest manifest,
                                                              List<SignalStorageRecord> inserts,
                                                              List<byte[]> deletes,
                                                              boolean clearAll)
      throws IOException, InvalidKeyException
  {
    ManifestRecord.Builder manifestRecordBuilder = ManifestRecord.newBuilder().setVersion(manifest.getVersion());

    for (StorageId id : manifest.getStorageIds()) {
      ManifestRecord.Identifier idProto = ManifestRecord.Identifier.newBuilder()
                                                                   .setRaw(ByteString.copyFrom(id.getRaw()))
                                                                   .setType(ManifestRecord.Identifier.Type.forNumber(id.getType())).build();
      manifestRecordBuilder.addIdentifiers(idProto);
    }

    String             authToken       = this.pushServiceSocket.getStorageAuth();
    StorageManifestKey manifestKey     = storageKey.deriveManifestKey(manifest.getVersion());
    byte[]             encryptedRecord = SignalStorageCipher.encrypt(manifestKey, manifestRecordBuilder.build().toByteArray());
    StorageManifest storageManifest = StorageManifest.newBuilder()
                                                     .setVersion(manifest.getVersion())
                                                     .setValue(ByteString.copyFrom(encryptedRecord))
                                                     .build();
    WriteOperation.Builder writeBuilder = WriteOperation.newBuilder().setManifest(storageManifest);

    for (SignalStorageRecord insert : inserts) {
      writeBuilder.addInsertItem(SignalStorageModels.localToRemoteStorageRecord(insert, storageKey));
    }

    if (clearAll) {
      writeBuilder.setClearAll(true);
    } else {
      for (byte[] delete : deletes) {
        writeBuilder.addDeleteKey(ByteString.copyFrom(delete));
      }
    }

    Optional<StorageManifest> conflict = this.pushServiceSocket.writeStorageContacts(authToken, writeBuilder.build());

    if (conflict.isPresent()) {
      StorageManifestKey conflictKey       = storageKey.deriveManifestKey(conflict.get().getVersion());
      byte[]             rawManifestRecord = SignalStorageCipher.decrypt(conflictKey, conflict.get().getValue().toByteArray());
      ManifestRecord     record            = ManifestRecord.parseFrom(rawManifestRecord);
      List<StorageId>    ids               = new ArrayList<>(record.getIdentifiersCount());

      for (ManifestRecord.Identifier id : record.getIdentifiersList()) {
        ids.add(StorageId.forType(id.getRaw().toByteArray(), id.getType().getNumber()));
      }

      SignalStorageManifest conflictManifest = new SignalStorageManifest(record.getVersion(), ids);

      return Optional.of(conflictManifest);
    } else {
      return Optional.absent();
    }
  }

  public Map<String, Object> getRemoteConfig() throws IOException {
    RemoteConfigResponse response = this.pushServiceSocket.getRemoteConfig();
    Map<String, Object>  out      = new HashMap<>();

    for (RemoteConfigResponse.Config config : response.getConfig()) {
      out.put(config.getName(), config.getValue() != null ? config.getValue() : config.isEnabled());
    }

    return out;
  }

  public String getNewDeviceVerificationCode() throws IOException {
    return this.pushServiceSocket.getNewDeviceVerificationCode();
  }

  public void addDevice(String deviceIdentifier,
                        ECPublicKey deviceKey,
                        IdentityKeyPair identityKeyPair,
                        Optional<byte[]> profileKey,
                        String code)
      throws InvalidKeyException, IOException
  {
    ProvisioningCipher cipher = new ProvisioningCipher(deviceKey);
    ProvisionMessage.Builder message = ProvisionMessage.newBuilder()
                                                       .setIdentityKeyPublic(ByteString.copyFrom(identityKeyPair.getPublicKey().serialize()))
                                                       .setIdentityKeyPrivate(ByteString.copyFrom(identityKeyPair.getPrivateKey().serialize()))
                                                       .setProvisioningCode(code)
                                                       .setProvisioningVersion(ProvisioningVersion.CURRENT_VALUE);
    String userLogin = credentials.getUserLogin();
    ACI    aci  = credentials.getAci();

    if (userLogin != null) {
      message.setNumber(userLogin);
    } else {
      throw new AssertionError("Missing user login!");
    }

    if (aci != null) {
      message.setUuid(aci.toString());
    } else {
      Log.w(TAG, "[addDevice] Missing UUID.");
    }

    if (profileKey.isPresent()) {
      message.setProfileKey(ByteString.copyFrom(profileKey.get()));
    }

    byte[] ciphertext = cipher.encrypt(message.build());
    this.pushServiceSocket.sendProvisioningMessage(deviceIdentifier, ciphertext);
  }

  public List<DeviceInfo> getDevices() throws IOException {
    return this.pushServiceSocket.getDevices();
  }

  public void removeDevice(long deviceId) throws IOException {
    this.pushServiceSocket.removeDevice(deviceId);
  }

  public TurnServerInfo getTurnServerInfo() throws IOException {
    return this.pushServiceSocket.getTurnServerInfo();
  }

  public ConfigurationInfo getConfigurationInfo() throws IOException {
    return this.pushServiceSocket.getConfigurationInfo();
  }

  public SystemCertificates getSystemCerts() throws IOException {
    return this.pushServiceSocket.getSystemCerts();
  }

  public SystemCertificatesVersion getCertVer() throws IOException {
    return this.pushServiceSocket.getCertVer();
  }

  public AttachmentV2UploadAttributes getDebugLogUploadAttributes() throws IOException {
    return this.pushServiceSocket.getDebugLogUploadAttributes();
  }

  public void checkNetworkConnection() throws IOException {
    this.pushServiceSocket.pingStorageService();
  }

  public CurrencyConversions getCurrencyConversions() throws IOException {
    return this.pushServiceSocket.getCurrencyConversions();
  }

  public void reportSpam(String e164, String serverGuid) throws IOException {
    this.pushServiceSocket.reportSpam(e164, serverGuid);
  }

  /**
   * @return The avatar URL path, if one was written.
   */
  public Optional<String> setVersionedProfile(ACI aci,
                                              ProfileKey profileKey,
                                              String name,
                                              String about,
                                              String aboutEmoji,
                                              Optional<SignalServiceProtos.PaymentAddress> paymentsAddress,
                                              StreamDetails avatar,
                                              List<String> visibleBadgeIds)
      throws IOException
  {
    if (name == null) name = "";

    ProfileCipher     profileCipher               = new ProfileCipher(profileKey);
    byte[]            ciphertextName              = profileCipher.encryptString(name, ProfileCipher.getTargetNameLength(name));
    byte[]            ciphertextAbout             = profileCipher.encryptString(about, ProfileCipher.getTargetAboutLength(about));
    byte[]            ciphertextEmoji             = profileCipher.encryptString(aboutEmoji, ProfileCipher.EMOJI_PADDED_LENGTH);
    byte[]            ciphertextMobileCoinAddress = paymentsAddress.transform(address -> profileCipher.encryptWithLength(address.toByteArray(), ProfileCipher.PAYMENTS_ADDRESS_CONTENT_SIZE)).orNull();
    boolean           hasAvatar                   = avatar != null;
    ProfileAvatarData profileAvatarData           = null;

    if (hasAvatar) {
      profileAvatarData = new ProfileAvatarData(avatar.getStream(),
                                                ProfileCipherOutputStream.getCiphertextLength(avatar.getLength()),
                                                avatar.getContentType(),
                                                new ProfileCipherOutputStreamFactory(profileKey));
    }

    return this.pushServiceSocket.writeProfile(new SignalServiceProfileWrite(profileKey.getProfileKeyVersion(aci.uuid()).serialize(),
                                                                             ciphertextName,
                                                                             ciphertextAbout,
                                                                             ciphertextEmoji,
                                                                             ciphertextMobileCoinAddress,
                                                                             hasAvatar,
                                                                             profileKey.getCommitment(aci.uuid()).serialize(),
                                                                             visibleBadgeIds),
                                               profileAvatarData);
  }

  public Optional<ProfileKeyCredential> resolveProfileKeyCredential(ACI aci, ProfileKey profileKey, Locale locale)
      throws NonSuccessfulResponseCodeException, PushNetworkException
  {
    try {
      ProfileAndCredential credential = this.pushServiceSocket.retrieveVersionedProfileAndCredential(aci.uuid(), profileKey, Optional.absent(), locale).get(10, TimeUnit.SECONDS);
      return credential.getProfileKeyCredential();
    } catch (InterruptedException | TimeoutException e) {
      throw new PushNetworkException(e);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof NonSuccessfulResponseCodeException) {
        throw (NonSuccessfulResponseCodeException) e.getCause();
      } else if (e.getCause() instanceof PushNetworkException) {
        throw (PushNetworkException) e.getCause();
      } else {
        throw new PushNetworkException(e);
      }
    }
  }

  public void setUsername(String username) throws IOException {
    this.pushServiceSocket.setUsername(username);
  }

  public void deleteUsername() throws IOException {
    this.pushServiceSocket.deleteUsername();
  }

  public void deleteAccount() throws IOException {
    this.pushServiceSocket.deleteAccount();
  }

  public void requestRateLimitPushChallenge() throws IOException {
    this.pushServiceSocket.requestRateLimitPushChallenge();
  }

  public void submitRateLimitPushChallenge(String challenge) throws IOException {
    this.pushServiceSocket.submitRateLimitPushChallenge(challenge);
  }

  public void submitRateLimitRecaptchaChallenge(String challenge, String recaptchaToken) throws IOException {
    this.pushServiceSocket.submitRateLimitRecaptchaChallenge(challenge, recaptchaToken);
  }

  public void setSoTimeoutMillis(long soTimeoutMillis) {
    this.pushServiceSocket.setSoTimeoutMillis(soTimeoutMillis);
  }

  public void cancelInFlightRequests() {
    this.pushServiceSocket.cancelInFlightRequests();
  }

  public void updatePushServiceSocket(SignalServiceConfiguration configuration) {
    this.pushServiceSocket.renewNetworkConfiguration(configuration);
  }

  public GroupsV2Api getGroupsV2Api() {
    return new GroupsV2Api(pushServiceSocket, groupsV2Operations);
  }

  public AuthCredentials getPaymentsAuthorization() throws IOException {
    return pushServiceSocket.getPaymentsAuthorization();
  }
}
