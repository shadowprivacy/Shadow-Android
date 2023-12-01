package su.sres.securesms.payments;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import su.sres.core.util.concurrent.SignalExecutors;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.DatabaseObserver;
import su.sres.securesms.database.PaymentDatabase;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.util.concurrent.SerialMonoLifoExecutor;

import java.util.UUID;
import java.util.concurrent.Executor;

public final class PaymentTransactionLiveData extends LiveData<PaymentDatabase.PaymentTransaction> {

  private final UUID                      paymentId;
  private final PaymentDatabase           paymentDatabase;
  private final DatabaseObserver.Observer observer;
  private final Executor                  executor;

  public PaymentTransactionLiveData(@NonNull UUID paymentId) {
    this.paymentId       = paymentId;
    this.paymentDatabase = DatabaseFactory.getPaymentDatabase(ApplicationDependencies.getApplication());
    this.observer        = this::getPaymentTransaction;
    this.executor        = new SerialMonoLifoExecutor(SignalExecutors.BOUNDED);
  }

  @Override
  protected void onActive() {
    getPaymentTransaction();
    ApplicationDependencies.getDatabaseObserver().registerPaymentObserver(paymentId, observer);
  }

  @Override
  protected void onInactive() {
    ApplicationDependencies.getDatabaseObserver().unregisterObserver(observer);
  }

  private void getPaymentTransaction() {
    executor.execute(() -> postValue(paymentDatabase.getPayment(paymentId)));
  }
}
