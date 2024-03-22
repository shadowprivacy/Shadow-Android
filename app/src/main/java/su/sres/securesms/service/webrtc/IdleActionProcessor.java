package su.sres.securesms.service.webrtc;

import androidx.annotation.NonNull;

import su.sres.securesms.components.webrtc.BroadcastVideoSink;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.events.WebRtcViewModel;
import su.sres.core.util.logging.Log;
import su.sres.securesms.groups.GroupId;
import su.sres.securesms.ringrtc.Camera;
import su.sres.securesms.ringrtc.RemotePeer;
import su.sres.securesms.service.webrtc.state.WebRtcServiceState;

import org.signal.ringrtc.CallException;
import org.signal.ringrtc.CallManager;
import org.webrtc.CapturerObserver;
import org.webrtc.VideoFrame;

import java.util.UUID;

import su.sres.signalservice.api.messages.calls.OfferMessage;

/**
 * Action handler for when the system is at rest. Mainly responsible
 * for starting pre-call state, starting an outgoing call, or receiving an
 * incoming call.
 */
public class IdleActionProcessor extends WebRtcActionProcessor {

  private static final String TAG = Log.tag(IdleActionProcessor.class);

  private final BeginCallActionProcessorDelegate beginCallDelegate;

  public IdleActionProcessor(@NonNull WebRtcInteractor webRtcInteractor) {
    super(webRtcInteractor, TAG);
    beginCallDelegate = new BeginCallActionProcessorDelegate(webRtcInteractor, TAG);
  }

  protected @NonNull WebRtcServiceState handleStartIncomingCall(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer remotePeer) {
    Log.i(TAG, "handleStartIncomingCall():");

    currentState = WebRtcVideoUtil.initializeVideo(context, webRtcInteractor.getCameraEventListener(), currentState);
    return beginCallDelegate.handleStartIncomingCall(currentState, remotePeer);
  }

  @Override
  protected @NonNull WebRtcServiceState handleOutgoingCall(@NonNull WebRtcServiceState currentState,
                                                           @NonNull RemotePeer remotePeer,
                                                           @NonNull OfferMessage.Type offerType)
  {
    Log.i(TAG, "handleOutgoingCall():");

    currentState = WebRtcVideoUtil.initializeVideo(context, webRtcInteractor.getCameraEventListener(), currentState);
    return beginCallDelegate.handleOutgoingCall(currentState, remotePeer, offerType);
  }

  @Override
  protected @NonNull WebRtcServiceState handlePreJoinCall(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer remotePeer) {
    Log.i(TAG, "handlePreJoinCall():");

    boolean isGroupCall = remotePeer.getRecipient().isPushV2Group();
    WebRtcActionProcessor processor = isGroupCall ? new GroupPreJoinActionProcessor(webRtcInteractor)
                                                  : new PreJoinActionProcessor(webRtcInteractor);

    currentState = WebRtcVideoUtil.initializeVanityCamera(WebRtcVideoUtil.initializeVideo(context, webRtcInteractor.getCameraEventListener(), currentState));

    currentState = currentState.builder()
                               .actionProcessor(processor)
                               .changeCallInfoState()
                               .callState(WebRtcViewModel.State.CALL_PRE_JOIN)
                               .callRecipient(remotePeer.getRecipient())
                               .build();

    return isGroupCall ? currentState.getActionProcessor().handlePreJoinCall(currentState, remotePeer)
                       : currentState;
  }

  @Override
  protected @NonNull WebRtcServiceState handleGroupCallRingUpdate(@NonNull WebRtcServiceState currentState,
                                                                  @NonNull RemotePeer remotePeerGroup,
                                                                  @NonNull GroupId.V2 groupId,
                                                                  long ringId,
                                                                  @NonNull UUID uuid,
                                                                  @NonNull CallManager.RingUpdate ringUpdate)
  {
    Log.i(TAG, "handleGroupCallRingUpdate(): recipient: " + remotePeerGroup.getId() + " ring: " + ringId + " update: " + ringUpdate);

    if (ringUpdate != CallManager.RingUpdate.REQUESTED) {
      DatabaseFactory.getGroupCallRingDatabase(context).insertOrUpdateGroupRing(ringId, System.currentTimeMillis(), ringUpdate);
      return currentState;
    } else if (DatabaseFactory.getGroupCallRingDatabase(context).isCancelled(ringId)) {
      try {
        Log.i(TAG, "Incoming ring request for already cancelled ring: " + ringId);
        webRtcInteractor.getCallManager().cancelGroupRing(groupId.getDecodedId(), ringId, null);
      } catch (CallException e) {
        Log.w(TAG, "Error while trying to cancel ring: " + ringId, e);
      }
      return currentState;
    }

    webRtcInteractor.peekGroupCallForRingingCheck(new GroupCallRingCheckInfo(remotePeerGroup.getId(), groupId, ringId, uuid, ringUpdate));

    return currentState;
  }

  @Override
  protected @NonNull WebRtcServiceState handleReceivedGroupCallPeekForRingingCheck(@NonNull WebRtcServiceState currentState, @NonNull GroupCallRingCheckInfo info, long deviceCount) {
    Log.i(tag, "handleReceivedGroupCallPeekForRingingCheck(): recipient: " + info.getRecipientId() + " ring: " + info.getRingId() + " deviceCount: " + deviceCount);

    if (DatabaseFactory.getGroupCallRingDatabase(context).isCancelled(info.getRingId())) {
      try {
        Log.i(TAG, "Ring was cancelled while getting peek info ring: " + info.getRingId());
        webRtcInteractor.getCallManager().cancelGroupRing(info.getGroupId().getDecodedId(), info.getRingId(), null);
      } catch (CallException e) {
        Log.w(TAG, "Error while trying to cancel ring: " + info.getRingId(), e);
      }
      return currentState;
    }

    if (deviceCount == 0) {
      Log.i(TAG, "No one in the group call, mark as expired and do not ring");
      DatabaseFactory.getGroupCallRingDatabase(context).insertOrUpdateGroupRing(info.getRingId(), System.currentTimeMillis(), CallManager.RingUpdate.EXPIRED_REQUEST);
      return currentState;
    }

    currentState = currentState.builder()
                               .actionProcessor(new IncomingGroupCallActionProcessor(webRtcInteractor))
                               .build();

    return currentState.getActionProcessor().handleGroupCallRingUpdate(currentState, new RemotePeer(info.getRecipientId()), info.getGroupId(), info.getRingId(), info.getRingerUuid(), info.getRingUpdate());
  }
}
