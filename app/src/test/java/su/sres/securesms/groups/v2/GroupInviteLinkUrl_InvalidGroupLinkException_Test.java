package su.sres.securesms.groups.v2;

import androidx.annotation.NonNull;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.junit.Test;
import su.sres.storageservice.protos.groups.GroupInviteLink;
import org.signal.zkgroup.InvalidInputException;
import su.sres.util.Base64UrlSafe;
import su.sres.securesms.util.Util;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertNull;

public final class GroupInviteLinkUrl_InvalidGroupLinkException_Test {

    @Test
    public void empty_string() throws GroupInviteLinkUrl.InvalidGroupLinkException, GroupInviteLinkUrl.UnknownGroupLinkVersionException {
        assertNull(GroupInviteLinkUrl.fromUri(""));
    }

    @Test
    public void not_a_url_string() throws GroupInviteLinkUrl.InvalidGroupLinkException, GroupInviteLinkUrl.UnknownGroupLinkVersionException {
        assertNull(GroupInviteLinkUrl.fromUri("abc"));
    }

    @Test
    public void wrong_host() throws GroupInviteLinkUrl.InvalidGroupLinkException, GroupInviteLinkUrl.UnknownGroupLinkVersionException {
        assertNull(GroupInviteLinkUrl.fromUri("https://x.shadowprivacy.com/#CjQKIAD34MKnGrBkzDztTATwjXt-9LhLLCIG9pgzvmz-NN-AEhCbwyTuxDfP2mrluK779H7o"));
    }

    @Test
    public void wrong_scheme() throws GroupInviteLinkUrl.InvalidGroupLinkException, GroupInviteLinkUrl.UnknownGroupLinkVersionException {
        assertNull(GroupInviteLinkUrl.fromUri("http://group.shadowprivacy.com/#CjQKIAD34MKnGrBkzDztTATwjXt-9LhLLCIG9pgzvmz-NN-AEhCbwyTuxDfP2mrluK779H7o"));
    }

    @Test
    public void has_path() {
        assertThatThrownBy(() -> GroupInviteLinkUrl.fromUri("https://group.shadowprivacy.com/not_expected/#CjQKIAD34MKnGrBkzDztTATwjXt-9LhLLCIG9pgzvmz-NN-AEhCbwyTuxDfP2mrluK779H7o"))
                .isInstanceOf(GroupInviteLinkUrl.InvalidGroupLinkException.class)
                .hasMessage("No path was expected in uri");
    }

    @Test
    public void missing_ref() {
        assertThatThrownBy(() -> GroupInviteLinkUrl.fromUri("https://group.shadowprivacy.com/"))
                .isInstanceOf(GroupInviteLinkUrl.InvalidGroupLinkException.class)
                .hasMessage("No reference was in the uri");
    }

    @Test
    public void empty_ref() {
        assertThatThrownBy(() -> GroupInviteLinkUrl.fromUri("https://group.shadowprivacy.com/#"))
                .isInstanceOf(GroupInviteLinkUrl.InvalidGroupLinkException.class)
                .hasMessage("No reference was in the uri");
    }

    @Test
    public void bad_base64() {
        assertThatThrownBy(() -> GroupInviteLinkUrl.fromUri("https://group.shadowprivacy.com/#CAESNAogpQEzURH6BON1bCS264cmTi37Yi6HTOReXZUEHdsBIgSEPCLfiL7k4wCX;mwVi31USVY"))
                .isInstanceOf(GroupInviteLinkUrl.InvalidGroupLinkException.class)
                .hasCauseExactlyInstanceOf(IOException.class);
    }

    @Test
    public void bad_protobuf() {
        assertThatThrownBy(() -> GroupInviteLinkUrl.fromUri("https://group.shadowprivacy.com/#CAESNAogpQEzURH6BON1bCS264cmTi37Yi6HTOReXZUEHdsBIgSEPCLfiL7k4wCXmwVi31USVY"))
                .isInstanceOf(GroupInviteLinkUrl.InvalidGroupLinkException.class)
                .hasCauseExactlyInstanceOf(InvalidProtocolBufferException.class);
    }

    @Test
    public void version_999_url() {
        String url = "https://group.shadowprivacy.com/#uj4zCiDMSxlNUvF4bQ3z3fYzGyZTFbJ1xEqWbPE3uZSD8bjOrxIP8NxV-0GUz3jpxMLR1rN3";

        assertThatThrownBy(() -> GroupInviteLinkUrl.fromUri(url))
                .isInstanceOf(GroupInviteLinkUrl.UnknownGroupLinkVersionException.class)
                .hasMessage("Uri contains no known group link content");
    }

    @Test
    public void bad_master_key_length() {
        byte[]            masterKeyBytes = Util.getSecretBytes(33);
        GroupLinkPassword password       = GroupLinkPassword.createNew();

        String encoding = createEncodedProtobuf(masterKeyBytes, password.serialize());

        String url = "https://group.shadowprivacy.com/#" + encoding;

        assertThatThrownBy(() -> GroupInviteLinkUrl.fromUri(url))
                .isInstanceOf(GroupInviteLinkUrl.InvalidGroupLinkException.class)
                .hasCauseExactlyInstanceOf(InvalidInputException.class);
    }

    private static String createEncodedProtobuf(@NonNull byte[] groupMasterKey,
                                                @NonNull byte[] passwordBytes)
    {
        return Base64UrlSafe.encodeBytesWithoutPadding(GroupInviteLink.newBuilder()
                .setV1Contents(GroupInviteLink.GroupInviteLinkContentsV1.newBuilder()
                        .setGroupMasterKey(ByteString.copyFrom(groupMasterKey))
                        .setInviteLinkPassword(ByteString.copyFrom(passwordBytes)))
                .build()
                .toByteArray());
    }

}