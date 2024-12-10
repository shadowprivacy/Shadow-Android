package su.sres.securesms.registration.fragments;

import static su.sres.securesms.registration.fragments.RegistrationViewDelegate.setDebugLogSubmitMultiTapView;
import static su.sres.securesms.util.CircularProgressButtonUtil.cancelSpinning;
import static su.sres.securesms.util.CircularProgressButtonUtil.setSpinning;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;

import com.dd.CircularProgressButton;
import com.google.android.gms.auth.api.phone.SmsRetriever;
import com.google.android.gms.auth.api.phone.SmsRetrieverClient;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.tasks.Task;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;
import su.sres.core.util.ThreadUtil;
import su.sres.securesms.LoggingFragment;
import su.sres.securesms.R;
import su.sres.securesms.components.LabeledEditText;
import su.sres.core.util.logging.Log;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.registration.util.RegistrationUserLoginInputController;
import su.sres.securesms.registration.viewmodel.RegistrationViewModel;
import su.sres.securesms.registration.VerifyAccountRepository.Mode;
import su.sres.securesms.util.Dialogs;
import su.sres.securesms.util.LifecycleDisposable;
import su.sres.securesms.util.PlayServicesUtil;
import su.sres.securesms.util.TextSecurePreferences;
import su.sres.securesms.util.ViewUtil;

public final class EnterUserLoginFragment extends LoggingFragment implements RegistrationUserLoginInputController.Callbacks {

  private static final String TAG = Log.tag(EnterUserLoginFragment.class);

  private LabeledEditText        userLogin;
  private CircularProgressButton register;
  private View                   cancel;
  private ScrollView             scrollView;
  private RegistrationViewModel  viewModel;

  private final LifecycleDisposable disposables = new LifecycleDisposable();

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_registration_enter_user_login, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    setDebugLogSubmitMultiTapView(view.findViewById(R.id.verify_header));

    userLogin  = view.findViewById(R.id.user_login);
    cancel     = view.findViewById(R.id.cancel_button);
    scrollView = view.findViewById(R.id.scroll_view);
    register   = view.findViewById(R.id.registerButton);

    RegistrationUserLoginInputController controller = new RegistrationUserLoginInputController(requireContext(),
                                                                                         userLogin,
                                                                                         true,
                                                                                         this);

    register.setOnClickListener(v -> handleRegister(requireContext()));

    disposables.bindTo(getViewLifecycleOwner().getLifecycle());
    viewModel = new ViewModelProvider(requireActivity()).get(RegistrationViewModel.class);

    if (viewModel.isReregister()) {
      cancel.setVisibility(View.VISIBLE);
      cancel.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());
    } else {
      cancel.setVisibility(View.GONE);
    }

    viewModel.getLiveUserLogin().observe(getViewLifecycleOwner(), controller::updateUserLogin);

// captcha off
//        if (viewModel.hasCaptchaToken()) {
    ThreadUtil.runOnMainDelayed(() -> handleRegister(requireContext()), 250);
//        }

  }

  private void handleRegister(@NonNull Context context) {

    if (TextUtils.isEmpty(this.userLogin.getText())) {
      Toast.makeText(context, getString(R.string.RegistrationActivity_you_must_specify_the_user_login), Toast.LENGTH_LONG).show();
      return;
    }

    final String userLogin = viewModel.getUserLogin();

    if (!isValid(userLogin)) {
      Dialogs.showAlertDialog(context,
                              getString(R.string.RegistrationActivity_invalid_user_login),
                              String.format(getString(R.string.RegistrationActivity_the_login_you_specified_s_is_invalid), userLogin));
      return;
    }

    PlayServicesUtil.PlayServicesStatus fcmStatus = PlayServicesUtil.getPlayServicesStatus(context);

    if (fcmStatus == PlayServicesUtil.PlayServicesStatus.SUCCESS) {
      handleRequestVerification(context, true);
    } else if (fcmStatus == PlayServicesUtil.PlayServicesStatus.MISSING) {
      handlePromptForNoPlayServices(context);
    } else if (fcmStatus == PlayServicesUtil.PlayServicesStatus.NEEDS_UPDATE) {
      GoogleApiAvailability.getInstance().getErrorDialog(requireActivity(), ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED, 0).show();
    } else {
      Dialogs.showAlertDialog(context,
                              getString(R.string.RegistrationActivity_play_services_error),
                              getString(R.string.RegistrationActivity_google_play_services_is_updating_or_unavailable));
    }
  }

  private void handleRequestVerification(@NonNull Context context, boolean fcmSupported) {
    setSpinning(register);
    disableAllEntries();

    if (fcmSupported) {
      SmsRetrieverClient client = SmsRetriever.getClient(context);
      Task<Void>         task   = client.startSmsRetriever();

      task.addOnSuccessListener(none -> {
        Log.i(TAG, "Successfully registered SMS listener.");
        requestVerificationCode(Mode.SMS_WITH_LISTENER);
      });

      task.addOnFailureListener(e -> {
        Log.w(TAG, "Failed to register SMS listener.", e);
        requestVerificationCode(Mode.SMS_WITHOUT_LISTENER);
      });
    } else {
      Log.i(TAG, "FCM is not supported, using no SMS listener");
      requestVerificationCode(Mode.SMS_WITHOUT_LISTENER);
    }
  }

  private void disableAllEntries() {
    userLogin.setEnabled(false);
    cancel.setVisibility(View.GONE);
  }

  private void enableAllEntries() {
    userLogin.setEnabled(true);

    if (viewModel.isReregister()) {
      cancel.setVisibility(View.VISIBLE);
    }
  }

  private void requestVerificationCode(@NonNull Mode mode) {
    NavController navController = NavHostFragment.findNavController(this);

    Disposable request = viewModel.requestVerificationCode(mode)
                                  .doOnSubscribe(unused -> TextSecurePreferences.setPushRegistered(ApplicationDependencies.getApplication(), false))
                                  .observeOn(AndroidSchedulers.mainThread())
                                  .subscribe(processor -> {
                                    if (processor.hasResult()) {
                                      navController.navigate(EnterUserLoginFragmentDirections.actionEnterVerificationCode());
                                    } else if (processor.localRateLimit()) {
                                      Log.i(TAG, "Unable to request verification code due to local rate limit");
                                      navController.navigate(EnterUserLoginFragmentDirections.actionEnterVerificationCode());
                                    } else if (processor.captchaRequired()) {
                                      Log.i(TAG, "Unable to request verification code due to captcha required");
                                      // captcha off
                                      // navController.navigate(EnterUserLoginFragmentDirections.actionRequestCaptcha());
                                    } else if (processor.rateLimit()) {
                                      Log.i(TAG, "Unable to request verification code due to rate limit");
                                      Toast.makeText(register.getContext(), R.string.RegistrationActivity_rate_limited_to_service, Toast.LENGTH_LONG).show();
                                    } else if (processor.isInvalidLogin()) {
                                      Log.w(TAG, "Invalid user login", processor.getError());
                                      Dialogs.showAlertDialog(requireContext(),
                                                              getString(R.string.RegistrationActivity_invalid_user_login),
                                                              String.format(getString(R.string.RegistrationActivity_the_login_you_specified_s_is_invalid), viewModel.getUserLogin()));
                                    } else {
                                      Log.i(TAG, "Unknown error during verification code request", processor.getError());
                                      Toast.makeText(register.getContext(), R.string.RegistrationActivity_unable_to_connect_to_service, Toast.LENGTH_LONG).show();
                                    }

                                    cancelSpinning(register);
                                    enableAllEntries();
                                  });

    disposables.add(request);
  }

  @Override
  public void onLoginFocused() {
    scrollView.postDelayed(() -> scrollView.smoothScrollTo(0, register.getBottom()), 250);
  }

  @Override
  public void onLoginInputNext(@NonNull View view) {
    // Intentionally left blank
  }

  @Override
  public void onLoginInputDone(@NonNull View view) {
    ViewUtil.hideKeyboard(requireContext(), view);
    handleRegister(requireContext());
  }

  @Override
  public void setUserLogin(@NonNull String userLogin) {
    viewModel.setUserLogin(userLogin);
  }

  private void handlePromptForNoPlayServices(@NonNull Context context) {
    new MaterialAlertDialogBuilder(context)
        .setTitle(R.string.RegistrationActivity_missing_google_play_services)
        .setMessage(R.string.RegistrationActivity_this_device_is_missing_google_play_services)
        .setPositiveButton(R.string.RegistrationActivity_i_understand, (dialog1, which) -> handleRequestVerification(context, false))
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  private boolean isValid(String userLogin) {
    return userLogin.matches("^[0-9a-z\\-]{3,}$");
  }
}