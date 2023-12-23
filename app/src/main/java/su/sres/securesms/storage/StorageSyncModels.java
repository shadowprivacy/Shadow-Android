package su.sres.securesms.storage;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import org.signal.zkgroup.groups.GroupMasterKey;

import su.sres.securesms.database.IdentityDatabase;
import su.sres.securesms.database.RecipientDatabase;
import su.sres.securesms.database.RecipientDatabase.RecipientSettings;
import su.sres.securesms.groups.GroupId;
import su.sres.securesms.keyvalue.UserLoginPrivacyValues;
import su.sres.signalservice.api.push.SignalServiceAddress;
import su.sres.signalservice.api.storage.SignalAccountRecord;
import su.sres.signalservice.api.storage.SignalContactRecord;
import su.sres.signalservice.api.storage.SignalGroupV1Record;
import su.sres.signalservice.api.storage.SignalGroupV2Record;
import su.sres.signalservice.api.storage.SignalStorageRecord;
import su.sres.signalservice.internal.storage.protos.AccountRecord;
import su.sres.signalservice.internal.storage.protos.ContactRecord.IdentityState;

import java.util.List;
import java.util.Set;

public final class StorageSyncModels {

    private StorageSyncModels() {}


    public static @NonNull SignalStorageRecord localToRemoteRecord(@NonNull RecipientSettings settings) {
        if (settings.getStorageId() == null) {
            throw new AssertionError("Must have a storage key!");
        }

        return localToRemoteRecord(settings, settings.getStorageId());
    }

    public static @NonNull SignalStorageRecord localToRemoteRecord(@NonNull RecipientSettings settings, @NonNull GroupMasterKey groupMasterKey) {
        if (settings.getStorageId() == null) {
            throw new AssertionError("Must have a storage key!");
        }

        return SignalStorageRecord.forGroupV2(localToRemoteGroupV2(settings, settings.getStorageId(), groupMasterKey));
    }

    public static @NonNull SignalStorageRecord localToRemoteRecord(@NonNull RecipientSettings settings, @NonNull byte[] rawStorageId) {
        switch (settings.getGroupType()) {
            case NONE:      return SignalStorageRecord.forContact(localToRemoteContact(settings, rawStorageId));
            case SIGNAL_V1: return SignalStorageRecord.forGroupV1(localToRemoteGroupV1(settings, rawStorageId));
            case SIGNAL_V2: return SignalStorageRecord.forGroupV2(localToRemoteGroupV2(settings, rawStorageId, settings.getSyncExtras().getGroupMasterKey()));
            default:        throw new AssertionError("Unsupported type!");
        }
    }

    public static AccountRecord.UserLoginSharingMode localToRemoteUserLoginSharingMode(UserLoginPrivacyValues.UserLoginSharingMode userLoginUserLoginSharingMode) {
        switch (userLoginUserLoginSharingMode) {
            case EVERYONE: return AccountRecord.UserLoginSharingMode.EVERYBODY;
            case NOBODY  : return AccountRecord.UserLoginSharingMode.NOBODY;
            default      : throw new AssertionError();
        }
    }

    public static UserLoginPrivacyValues.UserLoginSharingMode remoteToLocalUserLoginSharingMode(AccountRecord.UserLoginSharingMode userLoginUserLoginSharingMode) {
        switch (userLoginUserLoginSharingMode) {
            case EVERYBODY    : return UserLoginPrivacyValues.UserLoginSharingMode.EVERYONE;
            case NOBODY       : return UserLoginPrivacyValues.UserLoginSharingMode.NOBODY;
            default           : return UserLoginPrivacyValues.UserLoginSharingMode.NOBODY;
        }
    }

    public static List<SignalAccountRecord.PinnedConversation> localToRemotePinnedConversations(@NonNull List<RecipientSettings> settings) {
        return Stream.of(settings)
                .filter(s -> s.getGroupType() == RecipientDatabase.GroupType.SIGNAL_V1 ||
                        s.getGroupType() == RecipientDatabase.GroupType.SIGNAL_V2 ||
                        s.getRegistered() == RecipientDatabase.RegisteredState.REGISTERED)
                .map(StorageSyncModels::localToRemotePinnedConversation)
                .toList();
    }

    private static @NonNull SignalAccountRecord.PinnedConversation localToRemotePinnedConversation(@NonNull RecipientSettings settings) {
        switch (settings.getGroupType()) {
            case NONE     : return SignalAccountRecord.PinnedConversation.forContact(new SignalServiceAddress(settings.getUuid(), settings.getE164()));
            case SIGNAL_V1: return SignalAccountRecord.PinnedConversation.forGroupV1(settings.getGroupId().requireV1().getDecodedId());
            case SIGNAL_V2: return SignalAccountRecord.PinnedConversation.forGroupV2(settings.getSyncExtras().getGroupMasterKey().serialize());
            default       : throw new AssertionError("Unexpected group type!");
        }
    }

    private static @NonNull SignalContactRecord localToRemoteContact(@NonNull RecipientSettings recipient, byte[] rawStorageId) {
        if (recipient.getUuid() == null && recipient.getE164() == null) {
            throw new AssertionError("Must have either a UUID or a phone number!");
        }

        return new SignalContactRecord.Builder(rawStorageId, new SignalServiceAddress(recipient.getUuid(), recipient.getE164()))
                .setUnknownFields(recipient.getSyncExtras().getStorageProto())
                .setProfileKey(recipient.getProfileKey())
                .setGivenName(recipient.getProfileName().getGivenName())
                .setFamilyName(recipient.getProfileName().getFamilyName())
                .setBlocked(recipient.isBlocked())
                .setProfileSharingEnabled(recipient.isProfileSharing() || recipient.getSystemContactUri() != null)
                .setIdentityKey(recipient.getSyncExtras().getIdentityKey())
                .setIdentityState(localToRemoteIdentityState(recipient.getSyncExtras().getIdentityStatus()))
                .setArchived(recipient.getSyncExtras().isArchived())
                .setForcedUnread(recipient.getSyncExtras().isForcedUnread())
                .setMuteUntil(recipient.getMuteUntil())
                .build();
    }

    private static @NonNull SignalGroupV1Record localToRemoteGroupV1(@NonNull RecipientSettings recipient, byte[] rawStorageId) {
        GroupId groupId = recipient.getGroupId();

        if (groupId == null) {
            throw new AssertionError("Must have a groupId!");
        }

        if (!groupId.isV1()) {
            throw new AssertionError("Group is not V1");
        }

        return new SignalGroupV1Record.Builder(rawStorageId, groupId.getDecodedId())
                .setUnknownFields(recipient.getSyncExtras().getStorageProto())
                .setBlocked(recipient.isBlocked())
                .setProfileSharingEnabled(recipient.isProfileSharing())
                .setArchived(recipient.getSyncExtras().isArchived())
                .setForcedUnread(recipient.getSyncExtras().isForcedUnread())
                .setMuteUntil(recipient.getMuteUntil())
                .build();
    }

    private static @NonNull SignalGroupV2Record localToRemoteGroupV2(@NonNull RecipientSettings recipient, byte[] rawStorageId, @NonNull GroupMasterKey groupMasterKey) {
        GroupId groupId = recipient.getGroupId();

        if (groupId == null) {
            throw new AssertionError("Must have a groupId!");
        }

        if (!groupId.isV2()) {
            throw new AssertionError("Group is not V2");
        }

        if (groupMasterKey == null) {
            throw new AssertionError("Group master key not on recipient record");
        }

        return new SignalGroupV2Record.Builder(rawStorageId, groupMasterKey)
                .setUnknownFields(recipient.getSyncExtras().getStorageProto())
                .setBlocked(recipient.isBlocked())
                .setProfileSharingEnabled(recipient.isProfileSharing())
                .setArchived(recipient.getSyncExtras().isArchived())
                .setForcedUnread(recipient.getSyncExtras().isForcedUnread())
                .setMuteUntil(recipient.getMuteUntil())
                .build();
    }

    public static @NonNull IdentityDatabase.VerifiedStatus remoteToLocalIdentityStatus(@NonNull IdentityState identityState) {
        switch (identityState) {
            case VERIFIED:   return IdentityDatabase.VerifiedStatus.VERIFIED;
            case UNVERIFIED: return IdentityDatabase.VerifiedStatus.UNVERIFIED;
            default:         return IdentityDatabase.VerifiedStatus.DEFAULT;
        }
    }

    private static IdentityState localToRemoteIdentityState(@NonNull IdentityDatabase.VerifiedStatus local) {
        switch (local) {
            case VERIFIED:   return IdentityState.VERIFIED;
            case UNVERIFIED: return IdentityState.UNVERIFIED;
            default:         return IdentityState.DEFAULT;
        }
    }

}