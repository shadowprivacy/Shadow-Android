package su.sres.securesms.util;

import androidx.annotation.NonNull;

import java.util.concurrent.TimeUnit;

import su.sres.core.util.logging.Log;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.keyvalue.SignalStore;

public class SignalUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {

  private static final String TAG = Log.tag(SignalUncaughtExceptionHandler.class);

  private final Thread.UncaughtExceptionHandler originalHandler;

  public SignalUncaughtExceptionHandler(@NonNull Thread.UncaughtExceptionHandler originalHandler) {
    this.originalHandler = originalHandler;
  }

  @Override
  public void uncaughtException(@NonNull Thread t, @NonNull Throwable e) {
    Log.e(TAG, "", e, true);
    SignalStore.blockUntilAllWritesFinished();
    Log.blockUntilAllWritesFinished();
    ApplicationDependencies.getJobManager().flush();
    originalHandler.uncaughtException(t, e);
  }
}