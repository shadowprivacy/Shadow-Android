package su.sres.securesms.groups.ui.invitesandrequests.joining;

import su.sres.securesms.recipients.Recipient;

final class JoinGroupSuccess {
    private final Recipient groupRecipient;
    private final long      groupThreadId;

    JoinGroupSuccess(Recipient groupRecipient, long groupThreadId) {
        this.groupRecipient = groupRecipient;
        this.groupThreadId  = groupThreadId;
    }

    Recipient getGroupRecipient() {
        return groupRecipient;
    }

    long getGroupThreadId() {
        return groupThreadId;
    }
}
