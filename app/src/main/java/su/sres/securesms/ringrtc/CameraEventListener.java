package su.sres.securesms.ringrtc;

import androidx.annotation.NonNull;

public interface CameraEventListener {
    void onCameraSwitchCompleted(@NonNull CameraState newCameraState);
}