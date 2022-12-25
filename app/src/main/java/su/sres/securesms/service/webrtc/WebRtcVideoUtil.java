package su.sres.securesms.service.webrtc;

import android.content.Context;

import androidx.annotation.NonNull;

import su.sres.securesms.components.webrtc.BroadcastVideoSink;
import su.sres.securesms.ringrtc.Camera;
import su.sres.securesms.ringrtc.CameraEventListener;
import su.sres.securesms.ringrtc.CameraState;
import su.sres.securesms.service.webrtc.state.WebRtcServiceState;
import su.sres.securesms.service.webrtc.state.WebRtcServiceStateBuilder;
import su.sres.securesms.util.Util;
import org.webrtc.EglBase;

/**
 * Helper for initializing, reinitializing, and deinitializing the camera and it's related
 * infrastructure.
 */
public final class WebRtcVideoUtil {

    private WebRtcVideoUtil() {}

    public static @NonNull WebRtcServiceState initializeVideo(@NonNull Context context,
                                                              @NonNull CameraEventListener cameraEventListener,
                                                              @NonNull WebRtcServiceState currentState)
    {
        final WebRtcServiceStateBuilder builder = currentState.builder();

        Util.runOnMainSync(() -> {
            EglBase            eglBase   = EglBase.create();
            BroadcastVideoSink localSink = new BroadcastVideoSink(eglBase);
            Camera             camera    = new Camera(context, cameraEventListener, eglBase, CameraState.Direction.FRONT);

            builder.changeVideoState()
                    .eglBase(eglBase)
                    .localSink(localSink)
                    .camera(camera)
                    .commit()
                    .changeLocalDeviceState()
                    .cameraState(camera.getCameraState())
                    .commit();
        });

        return builder.build();
    }

    public static @NonNull WebRtcServiceState reinitializeCamera(@NonNull Context context,
                                                                 @NonNull CameraEventListener cameraEventListener,
                                                                 @NonNull WebRtcServiceState currentState)
    {
        final WebRtcServiceStateBuilder builder = currentState.builder();

        Util.runOnMainSync(() -> {
            Camera camera = currentState.getVideoState().requireCamera();
            camera.setEnabled(false);
            camera.dispose();

            camera = new Camera(context,
                    cameraEventListener,
                    currentState.getVideoState().requireEglBase(),
                    currentState.getLocalDeviceState().getCameraState().getActiveDirection());

            builder.changeVideoState()
                    .camera(camera)
                    .commit()
                    .changeLocalDeviceState()
                    .cameraState(camera.getCameraState())
                    .commit();
        });

        return builder.build();
    }

    public static @NonNull WebRtcServiceState deinitializeVideo(@NonNull WebRtcServiceState currentState) {
        Camera camera = currentState.getVideoState().getCamera();
        if (camera != null) {
            camera.dispose();
        }

        EglBase eglBase = currentState.getVideoState().getEglBase();
        if (eglBase != null) {
            eglBase.release();
        }

        return currentState.builder()
                .changeVideoState()
                .eglBase(null)
                .camera(null)
                .localSink(null)
                .commit()
                .changeLocalDeviceState()
                .cameraState(CameraState.UNKNOWN)
                .build();
    }
}