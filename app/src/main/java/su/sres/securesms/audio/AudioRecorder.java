package su.sres.securesms.audio;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;

import su.sres.core.util.ThreadUtil;
import su.sres.core.util.logging.Log;

import su.sres.securesms.components.voice.VoiceNoteDraft;
import su.sres.securesms.providers.BlobProvider;
import su.sres.securesms.util.MediaUtil;
import su.sres.securesms.util.concurrent.ListenableFuture;
import su.sres.securesms.util.concurrent.SettableFuture;
import su.sres.core.util.concurrent.SignalExecutors;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

public class AudioRecorder {

  private static final String TAG = Log.tag(AudioRecorder.class);

  private static final ExecutorService executor = SignalExecutors.newCachedSingleThreadExecutor("signal-AudioRecorder");

  private final Context context;

  private AudioCodec audioCodec;
  private Uri        captureUri;

  public AudioRecorder(@NonNull Context context) {
    this.context = context;
  }

  public void startRecording() {
    Log.i(TAG, "startRecording()");

    executor.execute(() -> {
      Log.i(TAG, "Running startRecording() + " + Thread.currentThread().getId());
      try {
        if (audioCodec != null) {
          throw new AssertionError("We can only record once at a time.");
        }

        ParcelFileDescriptor fds[] = ParcelFileDescriptor.createPipe();

        captureUri = BlobProvider.getInstance()
                                 .forData(new ParcelFileDescriptor.AutoCloseInputStream(fds[0]), 0)
                                 .withMimeType(MediaUtil.AUDIO_AAC)
                                 .createForDraftAttachmentAsync(context, () -> Log.i(TAG, "Write successful."), e -> Log.w(TAG, "Error during recording", e));
        audioCodec = new AudioCodec();

        audioCodec.start(new ParcelFileDescriptor.AutoCloseOutputStream(fds[1]));
      } catch (IOException e) {
        Log.w(TAG, e);
      }
    });
  }

  public @NonNull ListenableFuture<VoiceNoteDraft> stopRecording() {
    Log.i(TAG, "stopRecording()");

    final SettableFuture<VoiceNoteDraft> future = new SettableFuture<>();

    executor.execute(() -> {
      if (audioCodec == null) {
        sendToFuture(future, new IOException("MediaRecorder was never initialized successfully!"));
        return;
      }

      audioCodec.stop();

      try {
        long size = MediaUtil.getMediaSize(context, captureUri);
        sendToFuture(future, new VoiceNoteDraft(captureUri, size));
      } catch (IOException ioe) {
        Log.w(TAG, ioe);
        sendToFuture(future, ioe);
      }

      audioCodec = null;
      captureUri = null;
    });

    return future;
  }

  private <T> void sendToFuture(final SettableFuture<T> future, final Exception exception) {
    ThreadUtil.runOnMain(() -> future.setException(exception));
  }

  private <T> void sendToFuture(final SettableFuture<T> future, final T result) {
    ThreadUtil.runOnMain(() -> future.set(result));
  }
}
