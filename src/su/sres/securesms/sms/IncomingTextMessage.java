package su.sres.securesms.sms;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.telephony.SmsMessage;

import su.sres.securesms.database.Address;
import su.sres.securesms.util.GroupUtil;
import org.whispersystems.libsignal.util.guava.Optional;
import su.sres.signalservice.api.messages.SignalServiceGroup;
import su.sres.signalservice.api.push.SignalServiceAddress;

import java.util.List;

public class IncomingTextMessage implements Parcelable {

  public static final Parcelable.Creator<IncomingTextMessage> CREATOR = new Parcelable.Creator<IncomingTextMessage>() {
    @Override
    public IncomingTextMessage createFromParcel(Parcel in) {
      return new IncomingTextMessage(in);
    }

    @Override
    public IncomingTextMessage[] newArray(int size) {
      return new IncomingTextMessage[size];
    }
  };
  private static final String TAG = IncomingTextMessage.class.getSimpleName();

  private final String  message;
  private       Address sender;
  private final int     senderDeviceId;
  private final int     protocol;
  private final String  serviceCenterAddress;
  private final boolean replyPathPresent;
  private final String  pseudoSubject;
  private final long    sentTimestampMillis;
  private final Address groupId;
  private final boolean push;
  private final int     subscriptionId;
  private final long    expiresInMillis;
  private final long    revealDuration;
  private final boolean unidentified;

  public IncomingTextMessage(@NonNull Context context, @NonNull SmsMessage message, int subscriptionId) {
    this.message              = message.getDisplayMessageBody();
    this.sender               = Address.fromExternal(context, message.getDisplayOriginatingAddress());
    this.senderDeviceId       = SignalServiceAddress.DEFAULT_DEVICE_ID;
    this.protocol             = message.getProtocolIdentifier();
    this.serviceCenterAddress = message.getServiceCenterAddress();
    this.replyPathPresent     = message.isReplyPathPresent();
    this.pseudoSubject        = message.getPseudoSubject();
    this.sentTimestampMillis  = message.getTimestampMillis();
    this.subscriptionId       = subscriptionId;
    this.expiresInMillis      = 0;
    this.revealDuration       = 0;
    this.groupId              = null;
    this.push                 = false;
    this.unidentified         = false;
  }

  public IncomingTextMessage(Address sender, int senderDeviceId, long sentTimestampMillis,
                             String encodedBody, Optional<SignalServiceGroup> group,
                             long expiresInMillis, long revealDuration, boolean unidentified)
  {
    this.message              = encodedBody;
    this.sender               = sender;
    this.senderDeviceId       = senderDeviceId;
    this.protocol             = 31337;
    this.serviceCenterAddress = "GCM";
    this.replyPathPresent     = true;
    this.pseudoSubject        = "";
    this.sentTimestampMillis  = sentTimestampMillis;
    this.push                 = true;
    this.subscriptionId       = -1;
    this.expiresInMillis      = expiresInMillis;
    this.revealDuration       = revealDuration;
    this.unidentified         = unidentified;

    if (group.isPresent()) {
      this.groupId = Address.fromSerialized(GroupUtil.getEncodedId(group.get().getGroupId(), false));
    } else {
      this.groupId = null;
    }
  }

  public IncomingTextMessage(Parcel in) {
    this.message              = in.readString();
    this.sender               = in.readParcelable(IncomingTextMessage.class.getClassLoader());
    this.senderDeviceId       = in.readInt();
    this.protocol             = in.readInt();
    this.serviceCenterAddress = in.readString();
    this.replyPathPresent     = (in.readInt() == 1);
    this.pseudoSubject        = in.readString();
    this.sentTimestampMillis  = in.readLong();
    this.groupId              = in.readParcelable(IncomingTextMessage.class.getClassLoader());
    this.push                 = (in.readInt() == 1);
    this.subscriptionId       = in.readInt();
    this.expiresInMillis      = in.readLong();
    this.revealDuration       = in.readLong();
    this.unidentified         = in.readInt() == 1;
  }

  public IncomingTextMessage(IncomingTextMessage base, String newBody) {
    this.message              = newBody;
    this.sender               = base.getSender();
    this.senderDeviceId       = base.getSenderDeviceId();
    this.protocol             = base.getProtocol();
    this.serviceCenterAddress = base.getServiceCenterAddress();
    this.replyPathPresent     = base.isReplyPathPresent();
    this.pseudoSubject        = base.getPseudoSubject();
    this.sentTimestampMillis  = base.getSentTimestampMillis();
    this.groupId              = base.getGroupId();
    this.push                 = base.isPush();
    this.subscriptionId       = base.getSubscriptionId();
    this.expiresInMillis      = base.getExpiresIn();
    this.revealDuration       = base.getRevealDuration();
    this.unidentified         = base.isUnidentified();
  }

  public IncomingTextMessage(List<IncomingTextMessage> fragments) {
    StringBuilder body = new StringBuilder();

    for (IncomingTextMessage message : fragments) {
      body.append(message.getMessageBody());
    }

    this.message              = body.toString();
    this.sender               = fragments.get(0).getSender();
    this.senderDeviceId       = fragments.get(0).getSenderDeviceId();
    this.protocol             = fragments.get(0).getProtocol();
    this.serviceCenterAddress = fragments.get(0).getServiceCenterAddress();
    this.replyPathPresent     = fragments.get(0).isReplyPathPresent();
    this.pseudoSubject        = fragments.get(0).getPseudoSubject();
    this.sentTimestampMillis  = fragments.get(0).getSentTimestampMillis();
    this.groupId              = fragments.get(0).getGroupId();
    this.push                 = fragments.get(0).isPush();
    this.subscriptionId       = fragments.get(0).getSubscriptionId();
    this.expiresInMillis      = fragments.get(0).getExpiresIn();
    this.revealDuration       = fragments.get(0).getRevealDuration();
    this.unidentified         = fragments.get(0).isUnidentified();
  }

  protected IncomingTextMessage(@NonNull Address sender, @Nullable Address groupId)
  {
    this.message              = "";
    this.sender               = sender;
    this.senderDeviceId       = SignalServiceAddress.DEFAULT_DEVICE_ID;
    this.protocol             = 31338;
    this.serviceCenterAddress = "Outgoing";
    this.replyPathPresent     = true;
    this.pseudoSubject        = "";
    this.sentTimestampMillis  = System.currentTimeMillis();
    this.groupId              = groupId;
    this.push                 = true;
    this.subscriptionId       = -1;
    this.expiresInMillis      = 0;
    this.revealDuration       = 0;
    this.unidentified         = false;
  }

  public int getSubscriptionId() {
    return subscriptionId;
  }

  public long getExpiresIn() {
    return expiresInMillis;
  }

  public long getRevealDuration() {
    return revealDuration;
  }

  public long getSentTimestampMillis() {
    return sentTimestampMillis;
  }

  public String getPseudoSubject() {
    return pseudoSubject;
  }

  public String getMessageBody() {
    return message;
  }

  public IncomingTextMessage withMessageBody(String message) {
    return new IncomingTextMessage(this, message);
  }

  public Address getSender() {
    return sender;
  }

  public int getSenderDeviceId() {
    return senderDeviceId;
  }

  public int getProtocol() {
    return protocol;
  }

  public String getServiceCenterAddress() {
    return serviceCenterAddress;
  }

  public boolean isReplyPathPresent() {
    return replyPathPresent;
  }

  public boolean isSecureMessage() {
    return false;
  }

  public boolean isPreKeyBundle() {
    return isLegacyPreKeyBundle() || isContentPreKeyBundle();
  }

  public boolean isLegacyPreKeyBundle() {
    return false;
  }

  public boolean isContentPreKeyBundle() {
    return false;
  }

  public boolean isEndSession() {
    return false;
  }

  public boolean isPush() {
    return push;
  }

  public @Nullable Address getGroupId() {
    return groupId;
  }

  public boolean isGroup() {
    return false;
  }

  public boolean isJoined() {
    return false;
  }

  public boolean isIdentityUpdate() {
    return false;
  }

  public boolean isIdentityVerified() {
    return false;
  }

  public boolean isIdentityDefault() {
    return false;
  }

  public boolean isUnidentified() {
    return unidentified;
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel out, int flags) {
    out.writeString(message);
    out.writeParcelable(sender, flags);
    out.writeInt(senderDeviceId);
    out.writeInt(protocol);
    out.writeString(serviceCenterAddress);
    out.writeInt(replyPathPresent ? 1 : 0);
    out.writeString(pseudoSubject);
    out.writeLong(sentTimestampMillis);
    out.writeParcelable(groupId, flags);
    out.writeInt(push ? 1 : 0);
    out.writeInt(subscriptionId);
    out.writeLong(expiresInMillis);
    out.writeLong(revealDuration);
    out.writeInt(unidentified ? 1 : 0);
  }
}
