package su.sres.securesms.registration.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.navigation.Navigation;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.IOException;

import su.sres.securesms.R;

import su.sres.securesms.components.registration.VerificationCodeView;
import su.sres.securesms.components.registration.VerificationPinKeyboard;
import su.sres.securesms.events.ServerCertErrorEvent;
import su.sres.core.util.logging.Log;

import su.sres.securesms.registration.service.CodeVerificationRequest;
import su.sres.securesms.registration.service.RegistrationService;
import su.sres.securesms.registration.viewmodel.RegistrationViewModel;
import su.sres.securesms.util.CommunicationActions;
import su.sres.securesms.util.FeatureFlags;
import su.sres.securesms.util.SupportEmailUtil;
import su.sres.securesms.util.concurrent.AssertedSuccessListener;
import su.sres.securesms.util.concurrent.SimpleTask;


public final class EnterCodeFragment extends BaseRegistrationFragment
{

    private static final String TAG = Log.tag(EnterCodeFragment.class);

    private ScrollView              scrollView;
    private TextView                header;
    private VerificationCodeView    verificationCodeView;
    private VerificationPinKeyboard keyboard;
    private View                    serviceWarning;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_registration_enter_code, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        EventBus.getDefault().register(this);

        setDebugLogSubmitMultiTapView(view.findViewById(R.id.verify_header));

        scrollView           = view.findViewById(R.id.scroll_view);
        header               = view.findViewById(R.id.verify_header);
        verificationCodeView = view.findViewById(R.id.code);
        keyboard             = view.findViewById(R.id.keyboard);
        serviceWarning       = view.findViewById(R.id.cell_service_warning);

        connectKeyboard(verificationCodeView, keyboard);

        setOnCodeFullyEnteredListener(verificationCodeView);
    }

    private void setOnCodeFullyEnteredListener(VerificationCodeView verificationCodeView) {
        verificationCodeView.setOnCompleteListener(code -> {
            RegistrationViewModel model = getModel();

            model.onVerificationCodeEntered(code);

            keyboard.displayProgress();

            RegistrationService registrationService = RegistrationService.getInstance(model.getUserLogin(), model.getRegistrationSecret());

            registrationService.verifyAccount(requireActivity(), model.getFcmToken(), code, null,
                    new CodeVerificationRequest.VerifyCallback() {

                        @Override
                        public void onSuccessfulRegistration() {
                            SimpleTask.run(() -> {
                                long startTime = System.currentTimeMillis();
                                try {
                                    FeatureFlags.refreshSync();
                                    Log.i(TAG, "Took " + (System.currentTimeMillis() - startTime) + " ms to get feature flags.");
                                } catch (IOException e) {
                                    Log.w(TAG, "Failed to refresh flags after " + (System.currentTimeMillis() - startTime) + " ms.", e);
                                }

                                return null;
                            }, none -> {
                                keyboard.displaySuccess().addListener(new AssertedSuccessListener<Boolean>() {
                                    @Override
                                    public void onSuccess(Boolean result) {
                                        handleSuccessfulRegistration();
                                    }
                                });
                            });
                        }

                        @Override
                        public void onTooManyAttempts() {
                            keyboard.displayFailure().addListener(new AssertedSuccessListener<Boolean>() {
                                @Override
                                public void onSuccess(Boolean r) {
                                    new AlertDialog.Builder(requireContext())
                                            .setTitle(R.string.RegistrationActivity_too_many_attempts)
                                            .setMessage(R.string.RegistrationActivity_you_have_made_too_many_incorrect_registration_lock_pin_attempts_please_try_again_in_a_day)
                                            .setPositiveButton(android.R.string.ok, (dialog, which) -> {

                                                verificationCodeView.clear();
                                                keyboard.displayKeyboard();
                                            })
                                            .show();
                                }
                            });
                        }

                        @Override
                        public void onRetryAfter() {
                            Toast.makeText(requireContext(), R.string.RegistrationActivity_retry_after, Toast.LENGTH_LONG).show();
                            keyboard.displayFailure().addListener(new AssertedSuccessListener<Boolean>() {
                                @Override
                                public void onSuccess(Boolean result) {

                                    verificationCodeView.clear();
                                    keyboard.displayKeyboard();
                                }
                            });
                        }

                        @Override
                        public void onError() {
                            Toast.makeText(requireContext(), R.string.RegistrationActivity_error_connecting_to_service, Toast.LENGTH_LONG).show();
                            keyboard.displayFailure().addListener(new AssertedSuccessListener<Boolean>() {
                                @Override
                                public void onSuccess(Boolean result) {

                                    verificationCodeView.clear();
                                    keyboard.displayKeyboard();
                                }
                            });
                        }
                    });
        });
    }

    private void handleSuccessfulRegistration() {
        Navigation.findNavController(requireView()).navigate(EnterCodeFragmentDirections.actionSuccessfulRegistration());
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

        getModel().getLiveUserLogin().observe(getViewLifecycleOwner(), (s) -> header.setText(requireContext().getString(R.string.RegistrationActivity_enter_the_code_we_sent_to_s)));

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
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

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventServerCertError(ServerCertErrorEvent event) {
        Toast.makeText(getActivity(), event.message, Toast.LENGTH_LONG).show();
    }

}