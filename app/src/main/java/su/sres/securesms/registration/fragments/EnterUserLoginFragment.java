package su.sres.securesms.registration.fragments;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.dd.CircularProgressButton;
import com.google.android.gms.auth.api.phone.SmsRetriever;
import com.google.android.gms.auth.api.phone.SmsRetrieverClient;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.tasks.Task;

import su.sres.securesms.R;
import su.sres.securesms.components.LabeledEditText;
import su.sres.securesms.logging.Log;
import su.sres.securesms.registration.service.RegistrationCodeRequest;
import su.sres.securesms.registration.service.RegistrationService;
import su.sres.securesms.registration.viewmodel.RegistrationViewModel;
import su.sres.securesms.util.Dialogs;
import su.sres.securesms.util.PlayServicesUtil;

public final class EnterUserLoginFragment extends BaseRegistrationFragment {

    private static final String TAG = Log.tag(EnterUserLoginFragment.class);

    private LabeledEditText        userLogin;
    private CircularProgressButton register;
    private View                   cancel;
    private ScrollView             scrollView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_registration_enter_user_login, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setDebugLogSubmitMultiTapView(view.findViewById(R.id.verify_header));

        userLogin      = view.findViewById(R.id.user_login);
        cancel         = view.findViewById(R.id.cancel_button);
        scrollView     = view.findViewById(R.id.scroll_view);
        register       = view.findViewById(R.id.registerButton);

        setUpUserLoginInput();

        register.setOnClickListener(v -> handleRegister(requireContext()));

        if (isReregister()) {
            cancel.setVisibility(View.VISIBLE);
            cancel.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());
        } else {
            cancel.setVisibility(View.GONE);
        }

        RegistrationViewModel model  = getModel();
        String       userLogin = model.getUserLogin();

        initUserLogin(userLogin);

// captcha off
//        if (model.hasCaptchaToken()) {
            handleRegister(requireContext());
//        }

    }

    private void setUpUserLoginInput() {
        EditText userLoginInput = userLogin.getInput();

        userLoginInput.addTextChangedListener(new UserLoginChangedListener());

        userLogin.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                scrollView.postDelayed(() -> scrollView.smoothScrollTo(0, register.getBottom()), 250);
            }
        });

        userLoginInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        userLoginInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard(requireContext(), v);
                handleRegister(requireContext());
                return true;
            }
            return false;
        });
    }

    private void handleRegister(@NonNull Context context) {

        if (TextUtils.isEmpty(this.userLogin.getText())) {
            Toast.makeText(context, getString(R.string.RegistrationActivity_you_must_specify_the_user_login), Toast.LENGTH_LONG).show();
            return;
        }

        final String userLogin = getModel().getUserLogin();


        if (!isValid(userLogin)) {
            Dialogs.showAlertDialog(context,
                    getString(R.string.RegistrationActivity_invalid_user_login),
                    String.format(getString(R.string.RegistrationActivity_the_login_you_specified_s_is_invalid), userLogin));
            return;
        }

        PlayServicesUtil.PlayServicesStatus fcmStatus = PlayServicesUtil.getPlayServicesStatus(context);

        if (fcmStatus == PlayServicesUtil.PlayServicesStatus.SUCCESS) {
            handleRequestVerification(context, userLogin, true);
        } else if (fcmStatus == PlayServicesUtil.PlayServicesStatus.MISSING) {
            handlePromptForNoPlayServices(context, userLogin);
        } else if (fcmStatus == PlayServicesUtil.PlayServicesStatus.NEEDS_UPDATE) {
            GoogleApiAvailability.getInstance().getErrorDialog(requireActivity(), ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED, 0).show();
        } else {
            Dialogs.showAlertDialog(context, getString(R.string.RegistrationActivity_play_services_error),
                    getString(R.string.RegistrationActivity_google_play_services_is_updating_or_unavailable));
        }
    }

    private void handleRequestVerification(@NonNull Context context, @NonNull String userLogin, boolean fcmSupported) {
        setSpinning(register);
        disableAllEntries();

        if (fcmSupported) {
            SmsRetrieverClient client = SmsRetriever.getClient(context);
            Task<Void> task = client.startSmsRetriever();

            task.addOnSuccessListener(none -> {
                Log.i(TAG, "Successfully registered SMS listener.");
                requestVerificationCode(userLogin, RegistrationCodeRequest.Mode.SMS_WITH_LISTENER);
            });

            task.addOnFailureListener(e -> {
                Log.w(TAG, "Failed to register SMS listener.", e);
                requestVerificationCode(userLogin, RegistrationCodeRequest.Mode.SMS_WITHOUT_LISTENER);
            });
        } else {
            Log.i(TAG, "FCM is not supported, using no SMS listener");
            requestVerificationCode(userLogin, RegistrationCodeRequest.Mode.SMS_WITHOUT_LISTENER);
        }
    }

    private void disableAllEntries() {
        userLogin.setEnabled(false);
        cancel.setVisibility(View.GONE);
    }

    private void enableAllEntries() {
        userLogin.setEnabled(true);

        if (isReregister()) {
            cancel.setVisibility(View.VISIBLE);
        }
    }

    private void requestVerificationCode(String userLogin, @NonNull RegistrationCodeRequest.Mode mode) {
        RegistrationViewModel model   = getModel();
// captcha off
        //        String                captcha = model.getCaptchaToken();
//        model.clearCaptchaResponse();

        NavController navController = Navigation.findNavController(register);

        if (!model.getRequestLimiter().canRequest(mode, userLogin, System.currentTimeMillis())) {
            Log.i(TAG, "Local rate limited");
            navController.navigate(EnterUserLoginFragmentDirections.actionEnterVerificationCode());
            cancelSpinning(register);
            enableAllEntries();
            return;
        }

        RegistrationService registrationService = RegistrationService.getInstance(userLogin, model.getRegistrationSecret());

// captcha off
//        registrationService.requestVerificationCode(requireActivity(), mode, captcha,
        registrationService.requestVerificationCode(requireActivity(), mode,
                new RegistrationCodeRequest.SmsVerificationCodeCallback() {

                    @Override
                    public void onNeedCaptcha() {
                        if (getContext() == null) {
                            Log.i(TAG, "Got onNeedCaptcha response, but fragment is no longer attached.");
                            return;
                        }
                // captcha off
                        //        navController.navigate(EnterPhoneNumberFragmentDirections.actionRequestCaptcha());
                        cancelSpinning(register);
                        enableAllEntries();
                        model.getRequestLimiter().onUnsuccessfulRequest();
                        model.updateLimiter();
                    }

                    @Override
                    public void requestSent(@Nullable String fcmToken) {
                        if (getContext() == null) {
                            Log.i(TAG, "Got requestSent response, but fragment is no longer attached.");
                            return;
                        }
                        model.setFcmToken(fcmToken);
                        model.markASuccessfulAttempt();
                        navController.navigate(EnterUserLoginFragmentDirections.actionEnterVerificationCode());
                        cancelSpinning(register);
                        enableAllEntries();
                        model.getRequestLimiter().onSuccessfulRequest(mode, userLogin, System.currentTimeMillis());
                        model.updateLimiter();
                    }

                    @Override
                    public void onRateLimited() {
                        Toast.makeText(register.getContext(), R.string.RegistrationActivity_rate_limited_to_service, Toast.LENGTH_LONG).show();
                        cancelSpinning(register);
                        enableAllEntries();
                        model.getRequestLimiter().onUnsuccessfulRequest();
                        model.updateLimiter();
                    }

                    @Override
                    public void onError() {
                        Toast.makeText(register.getContext(), R.string.RegistrationActivity_unable_to_connect_to_service, Toast.LENGTH_LONG).show();
                        cancelSpinning(register);
                        enableAllEntries();
                        model.getRequestLimiter().onUnsuccessfulRequest();
                        model.updateLimiter();
                    }
                });
    }

    private void initUserLogin(@NonNull String userLogin) {

            this.userLogin.setText(userLogin);
    }

    private class UserLoginChangedListener implements TextWatcher {

        @Override
        public void afterTextChanged(Editable s) {
            String userLogin = s.toString();

            RegistrationViewModel model = getModel();

            model.setViewState(userLogin);

        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {

        }
    }

    private void handlePromptForNoPlayServices(@NonNull Context context, @NonNull String e164number) {
        new AlertDialog.Builder(context)
                .setTitle(R.string.RegistrationActivity_missing_google_play_services)
                .setMessage(R.string.RegistrationActivity_this_device_is_missing_google_play_services)
                .setPositiveButton(R.string.RegistrationActivity_i_understand, (dialog1, which) -> handleRequestVerification(context, e164number, false))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private boolean isValid(String userLogin) {
        return userLogin.matches("^[0-9a-z\\-]{3,}$");
    }
}