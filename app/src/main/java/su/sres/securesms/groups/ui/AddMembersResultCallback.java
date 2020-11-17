package su.sres.securesms.groups.ui;

import androidx.annotation.NonNull;

import java.util.List;

import su.sres.securesms.recipients.RecipientId;

public interface AddMembersResultCallback {
    void onMembersAdded(int numberOfMembersAdded, @NonNull List<RecipientId> invitedMembers);
}