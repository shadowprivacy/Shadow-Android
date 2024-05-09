package su.sres.securesms.giph.mp4;

import android.content.Context;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.MediaSourceFactory;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;

import okhttp3.OkHttpClient;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.net.ContentProxySelector;
import su.sres.securesms.video.exo.ChunkedDataSourceFactory;

/**
 * Provider which creates ExoPlayer instances for displaying Giphy content.
 */
final class GiphyMp4ExoPlayerProvider implements DefaultLifecycleObserver {

  private final Context            context;
  private final OkHttpClient       okHttpClient       = ApplicationDependencies.getOkHttpClient().newBuilder().proxySelector(new ContentProxySelector()).build();
  private final DataSource.Factory dataSourceFactory  = new ChunkedDataSourceFactory(okHttpClient, null);
  private final MediaSourceFactory mediaSourceFactory = new ProgressiveMediaSource.Factory(dataSourceFactory);

  GiphyMp4ExoPlayerProvider(@NonNull Context context) {
    this.context = context;
  }

  @MainThread final @NonNull ExoPlayer create() {
    SimpleExoPlayer exoPlayer = new SimpleExoPlayer.Builder(context)
        .setMediaSourceFactory(mediaSourceFactory)
        .build();

    exoPlayer.setRepeatMode(Player.REPEAT_MODE_ALL);
    exoPlayer.setVolume(0f);

    return exoPlayer;
  }
}
