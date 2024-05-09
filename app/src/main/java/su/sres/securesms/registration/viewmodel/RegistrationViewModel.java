package su.sres.securesms.registration.viewmodel;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AbstractSavedStateViewModelFactory;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import androidx.savedstate.SavedStateRegistryOwner;

import io.reactivex.rxjava3.core.Single;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.registration.RegistrationData;
import su.sres.securesms.registration.RegistrationRepository;
import su.sres.securesms.registration.VerifyAccountRepository;
import su.sres.securesms.registration.VerifyAccountResponseProcessor;
import su.sres.securesms.registration.VerifyAccountResponseWithoutKbs;
import su.sres.securesms.util.Util;
import su.sres.signalservice.internal.ServiceResponse;
import su.sres.signalservice.internal.push.VerifyAccountResponse;


public final class RegistrationViewModel extends BaseRegistrationViewModel {

  private static final String STATE_FCM_TOKEN          = "FCM_TOKEN";
  private static final String STATE_RESTORE_FLOW_SHOWN = "RESTORE_FLOW_SHOWN";
  private static final String STATE_IS_REREGISTER      = "IS_REREGISTER";

  private final RegistrationRepository registrationRepository;

  public RegistrationViewModel(@NonNull SavedStateHandle savedStateHandle,
                               boolean isReregister,
                               @NonNull VerifyAccountRepository verifyAccountRepository,
                               @NonNull RegistrationRepository registrationRepository)
  {
    super(savedStateHandle, verifyAccountRepository, Util.getSecret(18));

    this.registrationRepository = registrationRepository;

    setInitialDefaultValue(STATE_RESTORE_FLOW_SHOWN, false);

    this.savedState.set(STATE_IS_REREGISTER, isReregister);
  }

  public boolean isReregister() {
    //noinspection ConstantConditions
    return savedState.get(STATE_IS_REREGISTER);
  }

  public @Nullable String getFcmToken() {
    return savedState.get(STATE_FCM_TOKEN);
  }

  @MainThread
  public void setFcmToken(@Nullable String fcmToken) {
    savedState.set(STATE_FCM_TOKEN, fcmToken);
  }

  public void setWelcomeSkippedOnRestore() {
    savedState.set(STATE_RESTORE_FLOW_SHOWN, true);
  }

  public boolean hasRestoreFlowBeenShown() {
    //noinspection ConstantConditions
    return savedState.get(STATE_RESTORE_FLOW_SHOWN);
  }

  public void setIsReregister(boolean isReregister) {
    savedState.set(STATE_IS_REREGISTER, isReregister);
  }

  @Override
  protected Single<ServiceResponse<VerifyAccountResponse>> verifyAccountWithoutRegistrationLock() {
    return verifyAccountRepository.verifyAccount(getRegistrationData());
  }

  @Override
  protected Single<VerifyAccountResponseProcessor> onVerifySuccess(@NonNull VerifyAccountResponseProcessor processor) {
    return registrationRepository.registerAccountWithoutRegistrationLock(getRegistrationData(), processor.getResult())
                                 .map(VerifyAccountResponseWithoutKbs::new);
  }

  private RegistrationData getRegistrationData() {
    return new RegistrationData(getTextCodeEntered(),
                                getUserLogin(),
                                getRegistrationSecret(),
                                registrationRepository.getRegistrationId(),
                                registrationRepository.getProfileKey(getUserLogin()),
                                getFcmToken());
  }

  public static final class Factory extends AbstractSavedStateViewModelFactory {
    private final boolean isReregister;

    public Factory(@NonNull SavedStateRegistryOwner owner, boolean isReregister) {
      super(owner, null);
      this.isReregister = isReregister;
    }

    @Override
    protected @NonNull <T extends ViewModel> T create(@NonNull String key, @NonNull Class<T> modelClass, @NonNull SavedStateHandle handle) {
      //noinspection ConstantConditions
      return modelClass.cast(new RegistrationViewModel(handle,
                                                       isReregister,
                                                       new VerifyAccountRepository(ApplicationDependencies.getApplication()),
                                                       new RegistrationRepository(ApplicationDependencies.getApplication())));
    }
  }
}