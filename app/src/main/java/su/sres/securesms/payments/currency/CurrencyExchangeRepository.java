package su.sres.securesms.payments.currency;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;

import su.sres.core.util.concurrent.SignalExecutors;
import su.sres.core.util.logging.Log;
import su.sres.securesms.payments.Payments;
import su.sres.securesms.util.AsynchronousCallback;

import java.io.IOException;

public final class CurrencyExchangeRepository {

  private static final String TAG = Log.tag(CurrencyExchangeRepository.class);

  private final Payments payments;

  public CurrencyExchangeRepository(@NonNull Payments payments) {
    this.payments = payments;
  }

  @AnyThread
  public void getCurrencyExchange(@NonNull AsynchronousCallback.WorkerThread<CurrencyExchange, Throwable> callback, boolean refreshIfAble) {
    SignalExecutors.BOUNDED.execute(() -> {
      try {
        callback.onComplete(payments.getCurrencyExchange(refreshIfAble));
      } catch (IOException e) {
        Log.w(TAG, e);
        callback.onError(e);
      }
    });
  }
}
