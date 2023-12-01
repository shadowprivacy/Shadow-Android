package su.sres.securesms.payments.deactivate;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import su.sres.securesms.keyvalue.SignalStore;
import su.sres.securesms.payments.Balance;
import su.sres.securesms.payments.preferences.PaymentsHomeRepository;
import su.sres.securesms.util.SingleLiveEvent;
import su.sres.signalservice.api.payments.Money;

public class DeactivateWalletViewModel extends ViewModel {

  private final LiveData<Money>         balance;
  private final PaymentsHomeRepository  paymentsHomeRepository   = new PaymentsHomeRepository();
  private final SingleLiveEvent<Result> deactivatePaymentResults = new SingleLiveEvent<>();

  public DeactivateWalletViewModel() {
    balance = Transformations.map(SignalStore.paymentsValues().liveMobileCoinBalance(), Balance::getFullAmount);
  }

  void deactivateWallet() {
    paymentsHomeRepository.deactivatePayments(isDisabled -> deactivatePaymentResults.postValue(isDisabled ? Result.SUCCESS : Result.FAILED));
  }

  LiveData<Result> getDeactivationResults() {
    return deactivatePaymentResults;
  }

  LiveData<Money> getBalance() {
    return balance;
  }

  enum Result {
    SUCCESS,
    FAILED
  }
}
