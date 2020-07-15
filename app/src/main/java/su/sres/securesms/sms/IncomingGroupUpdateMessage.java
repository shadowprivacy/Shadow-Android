package su.sres.securesms.sms;

import su.sres.securesms.database.model.databaseprotos.DecryptedGroupV2Context;
import su.sres.securesms.mms.MessageGroupContext;

import static su.sres.signalservice.internal.push.SignalServiceProtos.GroupContext;

public final class IncomingGroupUpdateMessage extends IncomingTextMessage {

  private final MessageGroupContext groupContext;

  public IncomingGroupUpdateMessage(IncomingTextMessage base, GroupContext groupContext, String body) {
    this(base, new MessageGroupContext(groupContext));
  }

  public IncomingGroupUpdateMessage(IncomingTextMessage base, DecryptedGroupV2Context groupV2Context) {
    this(base, new MessageGroupContext(groupV2Context));
  }

  public IncomingGroupUpdateMessage(IncomingTextMessage base, MessageGroupContext groupContext) {
    super(base, groupContext.getEncodedGroupContext());
    this.groupContext = groupContext;
  }

  @Override
  public boolean isGroup() {
    return true;
  }

  public boolean isUpdate() {
    return groupContext.isV2Group() || groupContext.requireGroupV1Properties().isUpdate();
  }

  public boolean isGroupV2() {
    return groupContext.isV2Group();
  }

  public boolean isQuit() {
    return !groupContext.isV2Group() && groupContext.requireGroupV1Properties().isQuit();
  }

}
