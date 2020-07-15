package su.sres.securesms.groups;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.google.protobuf.ByteString;

import su.sres.securesms.database.model.databaseprotos.DecryptedGroupV2Context;
import su.sres.signalservice.api.util.UuidUtil;
import su.sres.signalservice.internal.push.SignalServiceProtos;
import su.sres.storageservice.protos.groups.local.DecryptedGroup;
import su.sres.storageservice.protos.groups.local.DecryptedGroupChange;
import su.sres.storageservice.protos.groups.local.DecryptedMember;
import su.sres.storageservice.protos.groups.local.DecryptedPendingMember;

import org.signal.zkgroup.groups.GroupMasterKey;
import org.signal.zkgroup.util.UUIDUtil;
import su.sres.securesms.recipients.Recipient;
import su.sres.signalservice.api.groupsv2.GroupsV2Operations;

import java.util.List;
import java.util.UUID;

public final class GroupProtoUtil {

    private GroupProtoUtil() {
    }

    public static int findVersionWeWereAdded(@NonNull DecryptedGroup group, @NonNull UUID uuid)
            throws GroupNotAMemberException
    {
        ByteString bytes = UuidUtil.toByteString(uuid);
        for (DecryptedMember decryptedMember : group.getMembersList()) {
            if (decryptedMember.getUuid().equals(bytes)) {
                return decryptedMember.getJoinedAtVersion();
            }
        }
        for (DecryptedPendingMember decryptedMember : group.getPendingMembersList()) {
            if (decryptedMember.getUuid().equals(bytes)) {
                // Assume latest, we don't have any information about when pending members were invited
                return group.getVersion();
            }
        }
        throw new GroupNotAMemberException();
    }

    public static DecryptedGroupV2Context createDecryptedGroupV2Context(@NonNull GroupMasterKey masterKey,
                                                                        @NonNull DecryptedGroup decryptedGroup,
                                                                        @Nullable DecryptedGroupChange plainGroupChange)
    {
        int version = plainGroupChange != null ? plainGroupChange.getVersion() : decryptedGroup.getVersion();
        SignalServiceProtos.GroupContextV2 groupContext = SignalServiceProtos.GroupContextV2.newBuilder()
                .setMasterKey(ByteString.copyFrom(masterKey.serialize()))
                .setRevision(version)
                .build();

        DecryptedGroupV2Context.Builder builder = DecryptedGroupV2Context.newBuilder()
                .setContext(groupContext)
                .setGroupState(decryptedGroup);

        if (plainGroupChange != null) {
            builder.setChange(plainGroupChange);
        }

        return builder.build();
    }

    @WorkerThread
    public static Recipient pendingMemberToRecipient(@NonNull Context context, @NonNull DecryptedPendingMember pendingMember) {
        return uuidByteStringToRecipient(context, pendingMember.getUuid());
    }

    @WorkerThread
    public static Recipient uuidByteStringToRecipient(@NonNull Context context, @NonNull ByteString uuidByteString) {
        UUID uuid = UUIDUtil.deserialize(uuidByteString.toByteArray());

        if (uuid.equals(GroupsV2Operations.UNKNOWN_UUID)) {
            return Recipient.UNKNOWN;
        }

        return Recipient.externalPush(context, uuid, null);
    }

    public static boolean isMember(@NonNull UUID uuid, @NonNull List<DecryptedMember> membersList) {
        ByteString uuidBytes = UuidUtil.toByteString(uuid);

        for (DecryptedMember member : membersList) {
            if (uuidBytes.equals(member.getUuid())) {
                return true;
            }
        }

        return false;
    }
}