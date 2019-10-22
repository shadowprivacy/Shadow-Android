package su.sres.securesms.registration.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
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
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import su.sres.securesms.BuildConfig;
import su.sres.securesms.R;

import su.sres.securesms.components.registration.VerificationCodeView;
import su.sres.securesms.components.registration.VerificationPinKeyboard;
import su.sres.securesms.logging.Log;

import su.sres.securesms.registration.service.CodeVerificationRequest;
import su.sres.securesms.registration.service.RegistrationService;
import su.sres.securesms.registration.viewmodel.RegistrationViewModel;
import su.sres.securesms.util.concurrent.AssertedSuccessListener;

import java.util.Locale;

public final class EnterCodeFragment extends BaseRegistrationFragment {

    private static final String TAG = Log.tag(EnterCodeFragment.class);

    private ScrollView              scrollView;
    private TextView                header;
    private VerificationCodeView    verificationCodeView;
    private VerificationPinKeyboard keyboard;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_registration_enter_code, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setDebugLogSubmitMultiTapView(view.findViewById(R.id.verify_header));

        scrollView           = view.findViewById(R.id.scroll_view);
        header               = view.findViewById(R.id.verify_header);
        verificationCodeView = view.findViewById(R.id.code);
        keyboard             = view.findViewById(R.id.keyboard);

        connectKeyboard(verificationCodeView, keyboard);

        setOnCodeFullyEnteredListener(verificationCodeView);


/*        getModel().getSuccessfulCodeRequestAttempts().observe(this, (attempts) -> {
            if (attempts >= 3) {

                scrollView.postDelayed(() -> scrollView.smoothScrollTo(0, noCodeReceivedHelp.getBottom()), 15000);
            }
        }); */
    }

    private void setOnCodeFullyEnteredListener(VerificationCodeView verificationCodeView) {
        verificationCodeView.setOnCompleteListener(code -> {
            RegistrationViewModel model = getModel();

            model.onVerificationCodeEntered(code);

            keyboard.displayProgress();

            RegistrationService registrationService = RegistrationService.getInstance(model.getNumber().getE164Number(), model.getRegistrationSecret());

            registrationService.verifyAccount(requireActivity(), model.getFcmToken(), code, null,
                    new CodeVerificationRequest.VerifyCallback() {

                        @Override
                        public void onSuccessfulRegistration() {
                            keyboard.displaySuccess().addListener(new AssertedSuccessListener<Boolean>() {
                                @Override
                                public void onSuccess(Boolean result) {
                                    handleSuccessfulRegistration();
                                }
                            });
                        }

                        @Override
                        public void onIncorrectRegistrationLockPin(long timeRemaining) {
                            keyboard.displayLocked().addListener(new AssertedSuccessListener<Boolean>() {
                                @Override
                                public void onSuccess(Boolean r) {
                                    Navigation.findNavController(requireView())
                                            .navigate(EnterCodeFragmentDirections.actionRequireRegistrationLockPin(timeRemaining));
                                }
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

    @Subscribe(threadMode = ThreadMode.MAIN)

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

        getModel().getLiveNumber().observe(this, (s) -> header.setText(requireContext().getString(R.string.RegistrationActivity_enter_the_code_we_sent_to_s)));

    }

    private void sendEmailToSupport() {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:"));
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{ getString(R.string.RegistrationActivity_support_email) });
        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.RegistrationActivity_code_support_subject));
        intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.RegistrationActivity_code_support_body,
                getDevice(),
                getAndroidVersion(),
                BuildConfig.VERSION_NAME,
                Locale.getDefault()));
        startActivity(intent);
    }

    private static String getDevice() {
        return String.format("%s %s (%s)", Build.MANUFACTURER, Build.MODEL, Build.PRODUCT);
    }

    private static String getAndroidVersion() {
        return String.format("%s (%s, %s)", Build.VERSION.RELEASE, Build.VERSION.INCREMENTAL, Build.DISPLAY);
    }
}