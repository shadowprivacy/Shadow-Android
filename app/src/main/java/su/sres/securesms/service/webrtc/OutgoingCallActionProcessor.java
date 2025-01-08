package su.sres.securesms.service.webrtc;

import android.os.ResultReceiver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.ringrtc.CallException;

import su.sres.securesms.components.webrtc.EglBaseWrapper;
import su.sres.securesms.crypto.IdentityKeyUtil;
import su.sres.securesms.database.ShadowDatabase;
import su.sres.securesms.events.CallParticipant;
import su.sres.securesms.events.WebRtcViewModel;
import su.sres.core.util.logging.Log;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientUtil;
import su.sres.securesms.ringrtc.RemotePeer;
import su.sres.securesms.service.webrtc.WebRtcData.CallMetadata;
import su.sres.securesms.service.webrtc.state.VideoState;
import su.sres.securesms.service.webrtc.state.WebRtcServiceState;
import su.sres.securesms.service.webrtc.state.WebRtcServiceStateBuilder;
import su.sres.securesms.util.NetworkUtil;

import org.signal.ringrtc.CallId;
import org.signal.ringrtc.CallManager;
import org.webrtc.PeerConnection;
import org.whispersystems.libsignal.InvalidKeyException;

import su.sres.securesms.webrtc.audio.SignalAudioManager;
import su.sres.signalservice.api.messages.calls.OfferMessage;

import java.util.List;
import java.util.Objects;

import static su.sres.securesms.webrtc.CallNotificationBuilder.TYPE_OUTGOING_RINGING;

/**
 * Responsible for setting up and managing the start of an outgoing 1:1 call. Transitioned
 * to from idle or pre-join and can either move to a connected state (callee picks up) or
 * a disconnected state (remote hangup, local hangup, etc.).
 */
public class OutgoingCallActionProcessor extends DeviceAwareActionProcessor {

  private static final String TAG = Log.tag(OutgoingCallActionProcessor.class);

  private final ActiveCallActionProcessorDelegate activeCallDelegate;
  private final CallSetupActionProcessorDelegate  callSetupDelegate;

  public OutgoingCallActionProcessor(@NonNull WebRtcInteractor webRtcInteractor) {
    super(webRtcInteractor, TAG);
    activeCallDelegate = new ActiveCallActionProcessorDelegate(webRtcInteractor, TAG);
    callSetupDelegate  = new CallSetupActionProcessorDelegate(webRtcInteractor, TAG);
  }

  @Override
  protected @NonNull WebRtcServiceState handleIsInCallQuery(@NonNull WebRtcServiceState currentState, @Nullable ResultReceiver resultReceiver) {
    return activeCallDelegate.handleIsInCallQuery(currentState, resultReceiver);
  }

  @Override
  protected @NonNull WebRtcServiceState handleStartOutgoingCall(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer remotePeer, @NonNull OfferMessage.Type offerType) {
    Log.i(TAG, "handleStartOutgoingCall():");
    WebRtcServiceStateBuilder builder = currentState.builder();

    remotePeer.dialing();

    Log.i(TAG, "assign activePeer callId: " + remotePeer.getCallId() + " key: " + remotePeer.hashCode() + " type: " + offerType);

    boolean isVideoCall = offerType == OfferMessage.Type.VIDEO_CALL;

    webRtcInteractor.setCallInProgressNotification(TYPE_OUTGOING_RINGING, remotePeer);
    webRtcInteractor.setDefaultAudioDevice(isVideoCall ? SignalAudioManager.AudioDevice.SPEAKER_PHONE
                                                       : SignalAudioManager.AudioDevice.EARPIECE,
                                           false);

    webRtcInteractor.updatePhoneState(WebRtcUtil.getInCallPhoneState(context));
    webRtcInteractor.initializeAudioForCall();
    webRtcInteractor.startOutgoingRinger();

    RecipientUtil.setAndSendUniversalExpireTimerIfNecessary(context, Recipient.resolved(remotePeer.getId()), ShadowDatabase.threads().getThreadIdIfExistsFor(remotePeer.getId()));
    ShadowDatabase.sms().insertOutgoingCall(remotePeer.getId(), isVideoCall);

    EglBaseWrapper.replaceHolder(EglBaseWrapper.OUTGOING_PLACEHOLDER, remotePeer.getCallId().longValue());

    webRtcInteractor.retrieveTurnServers(remotePeer);

    return builder.changeCallSetupState(remotePeer.getCallId())
                  .enableVideoOnCreate(isVideoCall)
                  .commit()
                  .changeCallInfoState()
                  .activePeer(remotePeer)
                  .callState(WebRtcViewModel.State.CALL_OUTGOING)
                  .commit()
                  .changeLocalDeviceState()
                  .build();
  }

  @Override
  public @NonNull WebRtcServiceState handleTurnServerUpdate(@NonNull WebRtcServiceState currentState,
                                                            @NonNull List<PeerConnection.IceServer> iceServers,
                                                            boolean isAlwaysTurn)
  {
    try {
      VideoState      videoState      = currentState.getVideoState();
      RemotePeer      activePeer      = currentState.getCallInfoState().requireActivePeer();
      CallParticipant callParticipant = Objects.requireNonNull(currentState.getCallInfoState().getRemoteCallParticipant(activePeer.getRecipient()));

      webRtcInteractor.getCallManager().proceed(activePeer.getCallId(),
                                                context,
                                                videoState.getLockableEglBase().require(),
                                                videoState.requireLocalSink(),
                                                callParticipant.getVideoSink(),
                                                videoState.requireCamera(),
                                                iceServers,
                                                isAlwaysTurn,
                                                NetworkUtil.getCallingBandwidthMode(context),
                                                currentState.getCallSetupState(activePeer).isEnableVideoOnCreate());
    } catch (CallException e) {
      return callFailure(currentState, "Unable to proceed with call: ", e);
    }

    return currentState.builder()
                       .changeLocalDeviceState()
                       .cameraState(currentState.getVideoState().requireCamera().getCameraState())
                       .build();
  }

  @Override
  protected @NonNull WebRtcServiceState handleRemoteRinging(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer remotePeer) {
    Log.i(TAG, "handleRemoteRinging(): call_id: " + remotePeer.getCallId());

    currentState.getCallInfoState().requireActivePeer().remoteRinging();
    return currentState.builder()
                       .changeCallInfoState()
                       .callState(WebRtcViewModel.State.CALL_RINGING)
                       .build();
  }

  @Override
  protected @NonNull WebRtcServiceState handleReceivedAnswer(@NonNull WebRtcServiceState currentState,
                                                             @NonNull CallMetadata callMetadata,
                                                             @NonNull WebRtcData.AnswerMetadata answerMetadata,
                                                             @NonNull WebRtcData.ReceivedAnswerMetadata receivedAnswerMetadata)
  {
    Log.i(TAG, "handleReceivedAnswer(): id: " + callMetadata.getCallId().format(callMetadata.getRemoteDevice()));

    if (answerMetadata.getOpaque() == null) {
      return callFailure(currentState, "receivedAnswer() failed: answerMetadata did not contain opaque", null);
    }

    try {
      byte[] remoteIdentityKey = WebRtcUtil.getPublicKeyBytes(receivedAnswerMetadata.getRemoteIdentityKey());
      byte[] localIdentityKey  = WebRtcUtil.getPublicKeyBytes(IdentityKeyUtil.getIdentityKey(context).serialize());

      webRtcInteractor.getCallManager().receivedAnswer(callMetadata.getCallId(), callMetadata.getRemoteDevice(), answerMetadata.getOpaque(), receivedAnswerMetadata.isMultiRing(), remoteIdentityKey, localIdentityKey);
    } catch (CallException | InvalidKeyException e) {
      return callFailure(currentState, "receivedAnswer() failed: ", e);
    }

    return currentState;
  }

  @Override
  protected @NonNull WebRtcServiceState handleReceivedBusy(@NonNull WebRtcServiceState currentState, @NonNull CallMetadata callMetadata) {
    Log.i(TAG, "handleReceivedBusy(): id: " + callMetadata.getCallId().format(callMetadata.getRemoteDevice()));

    try {
      webRtcInteractor.getCallManager().receivedBusy(callMetadata.getCallId(), callMetadata.getRemoteDevice());
    } catch (CallException e) {
      return callFailure(currentState, "receivedBusy() failed: ", e);
    }

    return currentState;
  }

  @Override
  protected @NonNull WebRtcServiceState handleSetMuteAudio(@NonNull WebRtcServiceState currentState, boolean muted) {
    return currentState.builder()
                       .changeLocalDeviceState()
                       .isMicrophoneEnabled(!muted)
                       .build();
  }

  @Override
  protected @NonNull WebRtcServiceState handleRemoteVideoEnable(@NonNull WebRtcServiceState currentState, boolean enable) {
    return activeCallDelegate.handleRemoteVideoEnable(currentState, enable);
  }

  @Override
  protected @NonNull WebRtcServiceState handleScreenSharingEnable(@NonNull WebRtcServiceState currentState, boolean enable) {
    return activeCallDelegate.handleScreenSharingEnable(currentState, enable);
  }

  @Override
  protected @NonNull WebRtcServiceState handleLocalHangup(@NonNull WebRtcServiceState currentState) {
    return activeCallDelegate.handleLocalHangup(currentState);
  }

  @Override
  protected @NonNull WebRtcServiceState handleReceivedOfferWhileActive(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer remotePeer) {
    return activeCallDelegate.handleReceivedOfferWhileActive(currentState, remotePeer);
  }

  @Override
  protected @NonNull WebRtcServiceState handleEndedRemote(@NonNull WebRtcServiceState currentState, @NonNull CallManager.CallEvent endedRemoteEvent, @NonNull RemotePeer remotePeer) {
    return activeCallDelegate.handleEndedRemote(currentState, endedRemoteEvent, remotePeer);
  }

  @Override
  protected @NonNull WebRtcServiceState handleEnded(@NonNull WebRtcServiceState currentState, @NonNull CallManager.CallEvent endedEvent, @NonNull RemotePeer remotePeer) {
    return activeCallDelegate.handleEnded(currentState, endedEvent, remotePeer);
  }

  @Override
  protected @NonNull WebRtcServiceState handleSetupFailure(@NonNull WebRtcServiceState currentState, @NonNull CallId callId) {
    return activeCallDelegate.handleSetupFailure(currentState, callId);
  }

  @Override
  public @NonNull WebRtcServiceState handleCallConnected(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer remotePeer) {
    return callSetupDelegate.handleCallConnected(currentState, remotePeer);
  }

  @Override
  protected @NonNull WebRtcServiceState handleSetEnableVideo(@NonNull WebRtcServiceState currentState, boolean enable) {
    return callSetupDelegate.handleSetEnableVideo(currentState, enable);
  }
}
