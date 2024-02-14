package su.sres.securesms.messagedetails;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import su.sres.securesms.database.documents.IdentityKeyMismatch;
import su.sres.securesms.database.documents.NetworkFailure;
import su.sres.securesms.database.model.MessageRecord;
import su.sres.securesms.recipients.Recipient;

final class RecipientDeliveryStatus {

    enum Status {
        UNKNOWN, PENDING, SENT, DELIVERED, READ, VIEWED
    }

    private final MessageRecord       messageRecord;
    private final Recipient           recipient;
    private final Status              deliveryStatus;
    private final boolean             isUnidentified;
    private final long                timestamp;
    private final NetworkFailure      networkFailure;
    private final IdentityKeyMismatch keyMismatchFailure;

    RecipientDeliveryStatus(@NonNull MessageRecord messageRecord, @NonNull Recipient recipient, @NonNull Status deliveryStatus, boolean isUnidentified, long timestamp, @Nullable NetworkFailure networkFailure, @Nullable IdentityKeyMismatch keyMismatchFailure) {
        this.messageRecord      = messageRecord;
        this.recipient          = recipient;
        this.deliveryStatus     = deliveryStatus;
        this.isUnidentified     = isUnidentified;
        this.timestamp          = timestamp;
        this.networkFailure     = networkFailure;
        this.keyMismatchFailure = keyMismatchFailure;
    }

    @NonNull MessageRecord getMessageRecord() {
        return messageRecord;
    }

    @NonNull Status getDeliveryStatus() {
        return deliveryStatus;
    }

    boolean isUnidentified() {
        return isUnidentified;
    }

    long getTimestamp() {
        return timestamp;
    }

    @NonNull Recipient getRecipient() {
        return recipient;
    }

    @Nullable NetworkFailure getNetworkFailure() {
        return networkFailure;
    }

    @Nullable IdentityKeyMismatch getKeyMismatchFailure() {
        return keyMismatchFailure;
    }
}