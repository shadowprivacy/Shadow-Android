package su.sres.securesms.service.webrtc;

import androidx.annotation.NonNull;

import su.sres.securesms.components.webrtc.EglBaseWrapper;
import su.sres.securesms.events.WebRtcViewModel;
import su.sres.core.util.logging.Log;
import su.sres.securesms.ringrtc.RemotePeer;
import su.sres.securesms.service.webrtc.state.WebRtcServiceState;
import su.sres.signalservice.api.messages.calls.OfferMessage;

/**
 * Handles pre-join call actions. This serves as a more capable idle state as no
 * call has actually start so incoming and outgoing calls are allowed.
 */
public class PreJoinActionProcessor extends DeviceAwareActionProcessor {

  private static final String TAG = Log.tag(PreJoinActionProcessor.class);

  private final BeginCallActionProcessorDelegate beginCallDelegate;

  public PreJoinActionProcessor(@NonNull WebRtcInteractor webRtcInteractor) {
    super(webRtcInteractor, TAG);
    beginCallDelegate = new BeginCallActionProcessorDelegate(webRtcInteractor, TAG);
  }

  @Override
  protected @NonNull WebRtcServiceState handleCancelPreJoinCall(@NonNull WebRtcServiceState currentState) {
    Log.i(TAG, "handleCancelPreJoinCall():");

    WebRtcVideoUtil.deinitializeVideo(currentState);
    EglBaseWrapper.releaseEglBase(EglBaseWrapper.OUTGOING_PLACEHOLDER);

    return new WebRtcServiceState(new IdleActionProcessor(webRtcInteractor));
  }

  protected @NonNull WebRtcServiceState handleStartIncomingCall(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer remotePeer) {
    Log.i(TAG, "handleStartIncomingCall():");

    EglBaseWrapper.replaceHolder(EglBaseWrapper.OUTGOING_PLACEHOLDER, remotePeer.getCallId().longValue());
    currentState = WebRtcVideoUtil.reinitializeCamera(context, webRtcInteractor.getCameraEventListener(), currentState)
                                  .builder()
                                  .changeCallInfoState()
                                  .callState(WebRtcViewModel.State.CALL_INCOMING)
                                  .build();

    webRtcInteractor.postStateUpdate(currentState);
    return beginCallDelegate.handleStartIncomingCall(currentState, remotePeer);
  }

  @Override
  protected @NonNull WebRtcServiceState handleOutgoingCall(@NonNull WebRtcServiceState currentState,
                                                           @NonNull RemotePeer remotePeer,
                                                           @NonNull OfferMessage.Type offerType)
  {
    Log.i(TAG, "handleOutgoingCall():");
    currentState = WebRtcVideoUtil.reinitializeCamera(context, webRtcInteractor.getCameraEventListener(), currentState);
    return beginCallDelegate.handleOutgoingCall(currentState, remotePeer, offerType);
  }

  @SuppressWarnings("ConstantConditions")
  @Override
  protected @NonNull WebRtcServiceState handleSetEnableVideo(@NonNull WebRtcServiceState currentState, boolean enable) {
    Log.i(TAG, "handleSetEnableVideo(): Changing for pre-join call.");

    currentState.getVideoState().getCamera().setEnabled(enable);
    return currentState.builder()
                       .changeLocalDeviceState()
                       .cameraState(currentState.getVideoState().getCamera().getCameraState())
                       .build();
  }

  @Override
  protected @NonNull WebRtcServiceState handleSetMuteAudio(@NonNull WebRtcServiceState currentState, boolean muted) {
    return currentState.builder()
                       .changeLocalDeviceState()
                       .isMicrophoneEnabled(!muted)
                       .build();
  }
}
