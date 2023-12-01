package su.sres.securesms.payments.backup;

import androidx.annotation.NonNull;

import su.sres.securesms.keyvalue.SignalStore;
import su.sres.securesms.payments.Mnemonic;

public final class PaymentsRecoveryRepository {
  public @NonNull Mnemonic getMnemonic() {
    return SignalStore.paymentsValues().getPaymentsMnemonic();
  }
}
