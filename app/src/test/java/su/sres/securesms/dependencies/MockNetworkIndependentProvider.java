package su.sres.securesms.dependencies;

import androidx.annotation.NonNull;

import su.sres.core.util.concurrent.DeadlockDetector;
import su.sres.securesms.crypto.storage.SignalSenderKeyStore;
import su.sres.securesms.crypto.storage.TextSecureIdentityKeyStore;
import su.sres.securesms.crypto.storage.TextSecurePreKeyStore;
import su.sres.securesms.crypto.storage.TextSecureSessionStore;
import su.sres.securesms.shakereport.ShakeToReport;
import su.sres.securesms.util.AppForegroundObserver;
import su.sres.securesms.video.exo.GiphyMp4Cache;
import su.sres.securesms.video.exo.SimpleExoPlayerPool;

import static org.mockito.Mockito.mock;

public class MockNetworkIndependentProvider implements ApplicationDependencies.NetworkIndependentProvider {

  @Override
  public @NonNull ShakeToReport provideShakeToReport() {
    return null;
  }

  @Override
  public @NonNull AppForegroundObserver provideAppForegroundObserver() {
    return mock(AppForegroundObserver.class);
  }

  @Override
  public @NonNull TextSecureIdentityKeyStore provideIdentityStore() {
    return null;
  }

  @Override
  public @NonNull TextSecureSessionStore provideSessionStore() {
    return null;
  }

  @Override
  public @NonNull TextSecurePreKeyStore providePreKeyStore() {
    return null;
  }

  @Override
  public @NonNull SignalSenderKeyStore provideSenderKeyStore() {
    return null;
  }

  @Override
  public @NonNull GiphyMp4Cache provideGiphyMp4Cache() {
    return null;
  }

  @Override
  public @NonNull SimpleExoPlayerPool provideExoPlayerPool() {
    return null;
  }

  @Override
  public @NonNull DeadlockDetector provideDeadlockDetector() {
    return null;
  }
}
