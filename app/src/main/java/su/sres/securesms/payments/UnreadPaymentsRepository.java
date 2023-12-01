package su.sres.securesms.payments;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import su.sres.core.util.concurrent.SignalExecutors;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.dependencies.ApplicationDependencies;

import java.util.UUID;
import java.util.concurrent.Executor;

public class UnreadPaymentsRepository {

  private static final Executor EXECUTOR = SignalExecutors.BOUNDED;

  public void markAllPaymentsSeen() {
    EXECUTOR.execute(this::markAllPaymentsSeenInternal);
  }

  public void markPaymentSeen(@NonNull UUID paymentId) {
    EXECUTOR.execute(() -> markPaymentSeenInternal(paymentId));
  }

  @WorkerThread
  private void markAllPaymentsSeenInternal() {
    Context context = ApplicationDependencies.getApplication();
    DatabaseFactory.getPaymentDatabase(context).markAllSeen();
  }

  @WorkerThread
  private void markPaymentSeenInternal(@NonNull UUID paymentId) {
    Context context = ApplicationDependencies.getApplication();
    DatabaseFactory.getPaymentDatabase(context).markPaymentSeen(paymentId);
  }

}
