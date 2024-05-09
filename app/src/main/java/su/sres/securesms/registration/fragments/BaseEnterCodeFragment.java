package su.sres.securesms.registration.fragments;

import static su.sres.securesms.registration.fragments.RegistrationViewDelegate.setDebugLogSubmitMultiTapView;

import android.os.Bundle;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.CallSuper;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.EventBus;

import su.sres.core.util.logging.Log;
import su.sres.securesms.LoggingFragment;
import su.sres.securesms.R;
import su.sres.securesms.components.registration.VerificationCodeView;
import su.sres.securesms.components.registration.VerificationPinKeyboard;
import su.sres.securesms.registration.viewmodel.BaseRegistrationViewModel;
import su.sres.securesms.util.CommunicationActions;
import su.sres.securesms.util.LifecycleDisposable;
import su.sres.securesms.util.SupportEmailUtil;
import su.sres.securesms.util.ViewUtil;
import su.sres.securesms.util.concurrent.AssertedSuccessListener;


import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;

/**
 * Base fragment used by registration and change number flow to input a verification code
 *
 * @param <ViewModel> - The concrete view model used by the subclasses, for ease of access in said subclass
 */
public abstract class BaseEnterCodeFragment<ViewModel extends BaseRegistrationViewModel> extends LoggingFragment {

  private static final String TAG = Log.tag(BaseEnterCodeFragment.class);

  private ScrollView scrollView;
  TextView header;
  private VerificationCodeView    verificationCodeView;
  private VerificationPinKeyboard keyboard;
  private View                    serviceWarning;

  private ViewModel viewModel;

  protected final LifecycleDisposable disposables = new LifecycleDisposable();

  public BaseEnterCodeFragment(@LayoutRes int contentLayoutId) {
    super(contentLayoutId);
  }

  @Override
  @CallSuper
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    setDebugLogSubmitMultiTapView(view.findViewById(R.id.verify_header));

    scrollView           = view.findViewById(R.id.scroll_view);
    header               = view.findViewById(R.id.verify_header);
    verificationCodeView = view.findViewById(R.id.code);
    keyboard             = view.findViewById(R.id.keyboard);
    serviceWarning       = view.findViewById(R.id.cell_service_warning);

    connectKeyboard(verificationCodeView, keyboard);
    ViewUtil.hideKeyboard(requireContext(), view);

    setOnCodeFullyEnteredListener(verificationCodeView);
  }

  protected abstract ViewModel getViewModel();

  protected abstract void handleSuccessfulVerify();

  // protected abstract void navigateToCaptcha();

  private void setOnCodeFullyEnteredListener(VerificationCodeView verificationCodeView) {
    verificationCodeView.setOnCompleteListener(code -> {
      keyboard.displayProgress();

      Disposable verify = viewModel.verifyCodeWithoutRegistrationLock(code)
                                   .observeOn(AndroidSchedulers.mainThread())
                                   .subscribe(processor -> {
                                     if (!processor.hasResult()) {
                                       Log.w(TAG, "post verify: ", processor.getError());
                                     }
                                     if (processor.hasResult()) {
                                       handleSuccessfulVerify();
                                     } else if (processor.rateLimit()) {
                                       handleRateLimited();
                                     } else if (processor.authorizationFailed()) {
                                       handleIncorrectCodeError();
                                     } else {
                                       Log.w(TAG, "Unable to verify code", processor.getError());
                                       handleGeneralError();
                                     }
                                   });

      disposables.add(verify);
    });
  }

  protected void displaySuccess(@NonNull Runnable runAfterAnimation) {
    keyboard.displaySuccess().addListener(new AssertedSuccessListener<Boolean>() {
      @Override
      public void onSuccess(Boolean result) {
        runAfterAnimation.run();
      }
    });
  }

  protected void handleRateLimited() {
    keyboard.displayFailure().addListener(new AssertedSuccessListener<Boolean>() {
      @Override
      public void onSuccess(Boolean r) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());

        builder.setTitle(R.string.RegistrationActivity_too_many_attempts)
               .setMessage(R.string.RegistrationActivity_you_have_made_too_many_attempts_please_try_again_later)
               .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                 verificationCodeView.clear();
                 keyboard.displayKeyboard();
               })
               .show();
      }
    });
  }

  protected void handleIncorrectCodeError() {
    Toast.makeText(requireContext(), R.string.RegistrationActivity_incorrect_code, Toast.LENGTH_LONG).show();
    keyboard.displayFailure().addListener(new AssertedSuccessListener<Boolean>() {
      @Override
      public void onSuccess(Boolean result) {
        verificationCodeView.clear();
        keyboard.displayKeyboard();
      }
    });
  }

  protected void handleGeneralError() {
    Toast.makeText(requireContext(), R.string.RegistrationActivity_error_connecting_to_service, Toast.LENGTH_LONG).show();
    keyboard.displayFailure().addListener(new AssertedSuccessListener<Boolean>() {
      @Override
      public void onSuccess(Boolean result) {
        verificationCodeView.clear();
        keyboard.displayKeyboard();
      }
    });
  }

  @Override
  public void onStart() {
    super.onStart();
    EventBus.getDefault().register(this);
  }

  @Override
  public void onStop() {
    super.onStop();
    EventBus.getDefault().unregister(this);
  }

  private void connectKeyboard(VerificationCodeView verificationCodeView, VerificationPinKeyboard keyboard) {
    keyboard.setOnKeyPressListener(key -> {
      if (key >= 0) {
        verificationCodeView.append(key);
      } else {
        verificationCodeView.delete();
      }
    });
  }

  @Override
  public void onResume() {
    super.onResume();

    header.setText(requireContext().getString(R.string.RegistrationActivity_enter_the_code_we_sent_to_s));
  }

  private void sendEmailToSupport() {
    String body = SupportEmailUtil.generateSupportEmailBody(requireContext(),
                                                            R.string.RegistrationActivity_code_support_subject,
                                                            null,
                                                            null);
    CommunicationActions.openEmail(requireContext(),
                                   SupportEmailUtil.getSupportEmailAddress(),
                                   getString(R.string.RegistrationActivity_code_support_subject),
                                   body);
  }
}
