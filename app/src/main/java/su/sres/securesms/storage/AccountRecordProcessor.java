package su.sres.securesms.storage;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import su.sres.core.util.logging.Log;
import su.sres.securesms.recipients.Recipient;

import org.whispersystems.libsignal.util.guava.Optional;

import su.sres.signalservice.api.storage.SignalAccountRecord;
import su.sres.signalservice.api.storage.SignalAccountRecord.PinnedConversation;
import su.sres.signalservice.internal.storage.protos.AccountRecord;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Processes {@link SignalAccountRecord}s. Unlike some other {@link StorageRecordProcessor}s, this
 * one has some statefulness in order to reject all but one account record (since we should have
 * exactly one account record).
 */
public class AccountRecordProcessor extends DefaultStorageRecordProcessor<SignalAccountRecord> {

  private static final String TAG = Log.tag(AccountRecordProcessor.class);

  private final Context             context;
  private final SignalAccountRecord localAccountRecord;
  private final Recipient           self;

  private boolean foundAccountRecord = false;

  public AccountRecordProcessor(@NonNull Context context, @NonNull Recipient self) {
    this(context, self, StorageSyncHelper.buildAccountRecord(context, self).getAccount().get());
  }

  AccountRecordProcessor(@NonNull Context context, @NonNull Recipient self, @NonNull SignalAccountRecord localAccountRecord) {
    this.context            = context;
    this.self               = self;
    this.localAccountRecord = localAccountRecord;
  }

  /**
   * We want to catch:
   * - Multiple account records
   */
  @Override
  boolean isInvalid(@NonNull SignalAccountRecord remote) {
    if (foundAccountRecord) {
      Log.w(TAG, "Found an additional account record! Considering it invalid.");
      return true;
    }

    foundAccountRecord = true;
    return false;
  }

  @Override
  public @NonNull Optional<SignalAccountRecord> getMatching(@NonNull SignalAccountRecord record, @NonNull StorageKeyGenerator keyGenerator) {
    return Optional.of(localAccountRecord);
  }

  @Override
  public @NonNull SignalAccountRecord merge(@NonNull SignalAccountRecord remote, @NonNull SignalAccountRecord local, @NonNull StorageKeyGenerator keyGenerator) {
    String givenName;
    String familyName;

    if (remote.getGivenName().isPresent() || remote.getFamilyName().isPresent()) {
      givenName  = remote.getGivenName().or("");
      familyName = remote.getFamilyName().or("");
    } else {
      givenName  = local.getGivenName().or("");
      familyName = local.getFamilyName().or("");
    }

    SignalAccountRecord.Payments payments;

    if (remote.getPayments().getEntropy().isPresent()) {
      payments = remote.getPayments();
    } else {
      payments = local.getPayments();
    }

    SignalAccountRecord.Subscriber subscriber;

    if (remote.getSubscriber().getId().isPresent()) {
      subscriber = remote.getSubscriber();
    } else {
      subscriber = local.getSubscriber();
    }

    byte[]                             unknownFields          = remote.serializeUnknownFields();
    String                             avatarUrlPath          = remote.getAvatarUrlPath().or(local.getAvatarUrlPath()).or("");
    byte[]                             profileKey             = remote.getProfileKey().or(local.getProfileKey()).orNull();
    boolean                            noteToSelfArchived     = remote.isNoteToSelfArchived();
    boolean                            noteToSelfForcedUnread = remote.isNoteToSelfForcedUnread();
    boolean                            readReceipts           = remote.isReadReceiptsEnabled();
    boolean                            typingIndicators       = remote.isTypingIndicatorsEnabled();
    boolean                            sealedSenderIndicators = remote.isSealedSenderIndicatorsEnabled();
    boolean                            linkPreviews           = remote.isLinkPreviewsEnabled();
    boolean                            unlisted               = remote.isUserLoginUnlisted();
    List<PinnedConversation>           pinnedConversations    = remote.getPinnedConversations();
    AccountRecord.UserLoginSharingMode userLoginSharingMode   = remote.getUserLoginSharingMode();
    int                                universalExpireTimer   = remote.getUniversalExpireTimer();
    String                             userLogin              = local.getUserLogin();
    List<String>                       defaultReactions       = remote.getDefaultReactions().size() > 0 ? remote.getDefaultReactions() : local.getDefaultReactions();
    boolean                              displayBadgesOnProfile = remote.isDisplayBadgesOnProfile();
    boolean                            matchesRemote          = doParamsMatch(remote, unknownFields, givenName, familyName, avatarUrlPath, profileKey, noteToSelfArchived, noteToSelfForcedUnread, readReceipts, typingIndicators, sealedSenderIndicators, linkPreviews, userLoginSharingMode, unlisted, pinnedConversations, payments, universalExpireTimer, userLogin, defaultReactions, subscriber, displayBadgesOnProfile);
    boolean                            matchesLocal           = doParamsMatch(local, unknownFields, givenName, familyName, avatarUrlPath, profileKey, noteToSelfArchived, noteToSelfForcedUnread, readReceipts, typingIndicators, sealedSenderIndicators, linkPreviews, userLoginSharingMode, unlisted, pinnedConversations, payments, universalExpireTimer, userLogin, defaultReactions, subscriber, displayBadgesOnProfile);

    if (matchesRemote) {
      return remote;
    } else if (matchesLocal) {
      return local;
    } else {
      return new SignalAccountRecord.Builder(keyGenerator.generate())
          .setUnknownFields(unknownFields)
          .setGivenName(givenName)
          .setFamilyName(familyName)
          .setAvatarUrlPath(avatarUrlPath)
          .setProfileKey(profileKey)
          .setNoteToSelfArchived(noteToSelfArchived)
          .setNoteToSelfForcedUnread(noteToSelfForcedUnread)
          .setReadReceiptsEnabled(readReceipts)
          .setTypingIndicatorsEnabled(typingIndicators)
          .setSealedSenderIndicatorsEnabled(sealedSenderIndicators)
          .setLinkPreviewsEnabled(linkPreviews)
          .setUnlistedUserLogin(unlisted)
          .setUserLoginSharingMode(userLoginSharingMode)
          .setUnlistedUserLogin(unlisted)
          .setPinnedConversations(pinnedConversations)
          .setPayments(payments.isEnabled(), payments.getEntropy().orNull())
          .setUniversalExpireTimer(universalExpireTimer)
          .setUserLogin(userLogin)
          .setDefaultReactions(defaultReactions)
          .setSubscriber(subscriber)
          .setDisplayBadgesOnProfile(displayBadgesOnProfile)
          .build();
    }
  }

  @Override
  void insertLocal(@NonNull SignalAccountRecord record) {
    throw new UnsupportedOperationException("We should always have a local AccountRecord, so we should never been inserting a new one.");
  }

  @Override
  void updateLocal(@NonNull StorageRecordUpdate<SignalAccountRecord> update) {
    StorageSyncHelper.applyAccountStorageSyncUpdates(context, self, update, true);
  }

  @Override
  public int compare(@NonNull SignalAccountRecord lhs, @NonNull SignalAccountRecord rhs) {
    return 0;
  }

  private static boolean doParamsMatch(@NonNull SignalAccountRecord contact,
                                       @Nullable byte[] unknownFields,
                                       @NonNull String givenName,
                                       @NonNull String familyName,
                                       @NonNull String avatarUrlPath,
                                       @Nullable byte[] profileKey,
                                       boolean noteToSelfArchived,
                                       boolean noteToSelfForcedUnread,
                                       boolean readReceipts,
                                       boolean typingIndicators,
                                       boolean sealedSenderIndicators,
                                       boolean linkPreviewsEnabled,
                                       AccountRecord.UserLoginSharingMode userLoginSharingMode,
                                       boolean unlistedPhoneNumber,
                                       @NonNull List<PinnedConversation> pinnedConversations,
                                       SignalAccountRecord.Payments payments,
                                       int universalExpireTimer,
                                       String userLogin,
                                       @NonNull List <String> defaultReactions,
                                       @NonNull SignalAccountRecord.Subscriber subscriber,
                                       boolean displayBadgesOnProfile)
  {
    return Arrays.equals(contact.serializeUnknownFields(), unknownFields) &&
           Objects.equals(contact.getGivenName().or(""), givenName) &&
           Objects.equals(contact.getFamilyName().or(""), familyName) &&
           Objects.equals(contact.getAvatarUrlPath().or(""), avatarUrlPath) &&
           Objects.equals(contact.getPayments(), payments) &&
           Objects.equals(contact.getUserLogin(), userLogin) &&
           Objects.equals(contact.getDefaultReactions(), defaultReactions)     &&
           Arrays.equals(contact.getProfileKey().orNull(), profileKey) &&
           contact.isNoteToSelfArchived() == noteToSelfArchived &&
           contact.isNoteToSelfForcedUnread() == noteToSelfForcedUnread &&
           contact.isReadReceiptsEnabled() == readReceipts &&
           contact.isTypingIndicatorsEnabled() == typingIndicators &&
           contact.isSealedSenderIndicatorsEnabled() == sealedSenderIndicators &&
           contact.isLinkPreviewsEnabled() == linkPreviewsEnabled &&
           contact.getUserLoginSharingMode() == userLoginSharingMode &&
           contact.isUserLoginUnlisted() == unlistedPhoneNumber &&
           contact.getUniversalExpireTimer() == universalExpireTimer &&
           Objects.equals(contact.getPinnedConversations(), pinnedConversations) &&
           Objects.equals(contact.getSubscriber(), subscriber)                   &&
           contact.isDisplayBadgesOnProfile() == displayBadgesOnProfile;
  }
}
