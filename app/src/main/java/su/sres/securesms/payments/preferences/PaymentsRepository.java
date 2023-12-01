package su.sres.securesms.payments.preferences;

import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;

import com.annimon.stream.Stream;

import su.sres.core.util.logging.Log;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.PaymentDatabase;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.securesms.payments.Direction;
import su.sres.securesms.payments.MobileCoinLedgerWrapper;
import su.sres.securesms.payments.Payment;
import su.sres.securesms.payments.reconciliation.LedgerReconcile;
import su.sres.securesms.util.livedata.LiveDataUtil;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * General repository for accessing payment information.
 */
public class PaymentsRepository {

  private static final String TAG = Log.tag(PaymentsRepository.class);

  private final PaymentDatabase         paymentDatabase;
  private final LiveData<List<Payment>> recentPayments;
  private final LiveData<List<Payment>> recentSentPayments;
  private final LiveData<List<Payment>> recentReceivedPayments;

  public PaymentsRepository() {
    paymentDatabase = DatabaseFactory.getPaymentDatabase(ApplicationDependencies.getApplication());

    LiveData<List<PaymentDatabase.PaymentTransaction>> localPayments = paymentDatabase.getAllLive();
    LiveData<MobileCoinLedgerWrapper>                  ledger        = SignalStore.paymentsValues().liveMobileCoinLedger();

    //noinspection NullableProblems
    this.recentPayments         = LiveDataUtil.mapAsync(LiveDataUtil.combineLatest(localPayments, ledger, Pair::create), p -> reconcile(p.first, p.second));
    this.recentSentPayments     = LiveDataUtil.mapAsync(this.recentPayments, p -> filterPayments(p, Direction.SENT));
    this.recentReceivedPayments = LiveDataUtil.mapAsync(this.recentPayments, p -> filterPayments(p, Direction.RECEIVED));
  }

  @WorkerThread
  private @NonNull List<Payment> reconcile(@NonNull Collection<PaymentDatabase.PaymentTransaction> paymentTransactions, @NonNull MobileCoinLedgerWrapper ledger) {
    List<Payment> reconcile = LedgerReconcile.reconcile(paymentTransactions, ledger);

    updateDatabaseWithNewBlockInformation(reconcile);

    return reconcile;
  }

  private void updateDatabaseWithNewBlockInformation(@NonNull List<Payment> reconcileOutput) {
    List<LedgerReconcile.BlockOverridePayment> blockOverridePayments = Stream.of(reconcileOutput)
                                                                             .select(LedgerReconcile.BlockOverridePayment.class)
                                                                             .toList();

    if (blockOverridePayments.isEmpty()) {
      return;
    }
    Log.i(TAG, String.format(Locale.US, "%d payments have new block index or timestamp information", blockOverridePayments.size()));

    for (LedgerReconcile.BlockOverridePayment blockOverridePayment : blockOverridePayments) {
      Payment inner    = blockOverridePayment.getInner();
      boolean override = false;
      if (inner.getBlockIndex() != blockOverridePayment.getBlockIndex()) {
        override = true;
      }
      if (inner.getBlockTimestamp() != blockOverridePayment.getBlockTimestamp()) {
        override = true;
      }
      if (!override) {
        Log.w(TAG, "  Unnecessary");
      } else {
        if (paymentDatabase.updateBlockDetails(inner.getUuid(), blockOverridePayment.getBlockIndex(), blockOverridePayment.getBlockTimestamp())) {
          Log.d(TAG, "  Updated block details for " + inner.getUuid());
        } else {
          Log.w(TAG, "  Failed to update block details for " + inner.getUuid());
        }
      }
    }
  }

  public @NonNull LiveData<List<Payment>> getRecentPayments() {
    return recentPayments;
  }

  public @NonNull LiveData<List<Payment>> getRecentSentPayments() {
    return recentSentPayments;
  }

  public @NonNull LiveData<List<Payment>> getRecentReceivedPayments() {
    return recentReceivedPayments;
  }

  private @NonNull List<Payment> filterPayments(@NonNull List<Payment> payments,
                                                @NonNull Direction direction)
  {
    return Stream.of(payments)
                 .filter(p -> p.getDirection() == direction)
                 .toList();
  }
}
