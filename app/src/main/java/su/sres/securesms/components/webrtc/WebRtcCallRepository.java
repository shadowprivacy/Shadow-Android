package su.sres.securesms.components.webrtc;

import android.media.AudioManager;

import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.util.ServiceUtil;

class WebRtcCallRepository {

    private final AudioManager audioManager;

    WebRtcCallRepository() {
        this.audioManager = ServiceUtil.getAudioManager(ApplicationDependencies.getApplication());
    }

    WebRtcAudioOutput getAudioOutput() {
        if (audioManager.isBluetoothScoOn()) {
            return WebRtcAudioOutput.HEADSET;
        } else if (audioManager.isSpeakerphoneOn()) {
            return WebRtcAudioOutput.SPEAKER;
        } else {
            return WebRtcAudioOutput.HANDSET;
        }
    }
}