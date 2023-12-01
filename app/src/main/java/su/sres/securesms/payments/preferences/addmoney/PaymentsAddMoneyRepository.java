package su.sres.securesms.payments.preferences.addmoney;

import android.net.Uri;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.securesms.payments.MobileCoinPublicAddress;
import su.sres.securesms.util.AsynchronousCallback;

final class PaymentsAddMoneyRepository {

  @MainThread
  void getWalletAddress(@NonNull AsynchronousCallback.MainThread<AddressAndUri, Error> callback) {
    if (!SignalStore.paymentsValues().mobileCoinPaymentsEnabled()) {
      callback.onError(Error.PAYMENTS_NOT_ENABLED);
    }

    MobileCoinPublicAddress publicAddress        = ApplicationDependencies.getPayments().getWallet().getMobileCoinPublicAddress();
    String                  paymentAddressBase58 = publicAddress.getPaymentAddressBase58();
    Uri                     paymentAddressUri    = publicAddress.getPaymentAddressUri();

    callback.onComplete(new AddressAndUri(paymentAddressBase58, paymentAddressUri));
  }

  enum Error {
    PAYMENTS_NOT_ENABLED
  }

}
