package su.sres.securesms.dependencies;

import android.app.Application;

import androidx.annotation.NonNull;

import su.sres.securesms.crypto.storage.SignalSenderKeyStore;
import su.sres.securesms.crypto.storage.TextSecureIdentityKeyStore;
import su.sres.securesms.crypto.storage.TextSecurePreKeyStore;
import su.sres.securesms.crypto.storage.TextSecureSessionStore;
import su.sres.securesms.keyvalue.KeyValueStore;
import su.sres.securesms.shakereport.ShakeToReport;
import su.sres.securesms.util.AppForegroundObserver;
import su.sres.securesms.util.ByteUnit;
import su.sres.securesms.video.exo.GiphyMp4Cache;
import su.sres.securesms.video.exo.SimpleExoPlayerPool;

// Here goes all the stuff that must be initialized prior to the service URL having been set
public class NetworkIndependentProvider implements ApplicationDependencies.NetworkIndependentProvider {

  private final Application context;

  public NetworkIndependentProvider(@NonNull Application context) {
    this.context = context;
  }

  public @NonNull
  KeyValueStore provideKeyValueStore() {
    return new KeyValueStore(context);
  }

  @Override
  public @NonNull TextSecureIdentityKeyStore provideIdentityStore() {
    return new TextSecureIdentityKeyStore(context);
  }

  @Override
  public @NonNull TextSecureSessionStore provideSessionStore() {
    return new TextSecureSessionStore(context);
  }

  @Override
  public @NonNull TextSecurePreKeyStore providePreKeyStore() {
    return new TextSecurePreKeyStore(context);
  }

  @Override
  public @NonNull SignalSenderKeyStore provideSenderKeyStore() {
    return new SignalSenderKeyStore(context);
  }

  @Override
  public @NonNull GiphyMp4Cache provideGiphyMp4Cache() {
    return new GiphyMp4Cache(ByteUnit.MEGABYTES.toBytes(16));
  }

  @Override
  public @NonNull ShakeToReport provideShakeToReport() {
    return new ShakeToReport(context);
  }

  @Override
  public @NonNull AppForegroundObserver provideAppForegroundObserver() {
    return new AppForegroundObserver();
  }

  @Override
  public @NonNull SimpleExoPlayerPool provideExoPlayerPool() {
    return new SimpleExoPlayerPool(context);
  }

}