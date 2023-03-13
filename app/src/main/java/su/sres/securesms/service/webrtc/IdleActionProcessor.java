package su.sres.securesms.service.webrtc;

import androidx.annotation.NonNull;

import su.sres.securesms.components.webrtc.BroadcastVideoSink;
import su.sres.securesms.events.WebRtcViewModel;
import su.sres.core.util.logging.Log;
import su.sres.securesms.ringrtc.Camera;
import su.sres.securesms.ringrtc.RemotePeer;
import su.sres.securesms.service.webrtc.state.WebRtcServiceState;
import org.webrtc.CapturerObserver;
import org.webrtc.VideoFrame;
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

        boolean               isGroupCall = remotePeer.getRecipient().isPushV2Group();
        WebRtcActionProcessor processor   = isGroupCall ? new GroupPreJoinActionProcessor(webRtcInteractor)
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
}
