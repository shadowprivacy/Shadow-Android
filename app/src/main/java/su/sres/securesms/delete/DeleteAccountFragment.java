package su.sres.securesms.delete;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;

import org.whispersystems.libsignal.util.guava.Optional;

import su.sres.securesms.R;
import su.sres.securesms.components.LabeledEditText;
import su.sres.securesms.util.SpanUtil;
import su.sres.securesms.util.ViewUtil;
import su.sres.securesms.util.text.AfterTextChanged;
import su.sres.securesms.util.views.SimpleProgressDialog;

public class DeleteAccountFragment extends Fragment {
    private TextView               bullets;
    private LabeledEditText        userLogin;
    private DeleteAccountViewModel viewModel;
    private DialogInterface        deletionProgressDialog;

    @Override
    public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.delete_account_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        View            confirm        = view.findViewById(R.id.delete_account_fragment_delete);

        bullets     = view.findViewById(R.id.delete_account_fragment_bullets);
        userLogin      = view.findViewById(R.id.delete_account_fragment_number);

        viewModel = ViewModelProviders.of(requireActivity(), new DeleteAccountViewModel.Factory(new DeleteAccountRepository()))
                .get(DeleteAccountViewModel.class);
        viewModel.getEvents().observe(getViewLifecycleOwner(), this::handleEvent);
        viewModel.getWalletBalance().observe(getViewLifecycleOwner(), this::updateBullets);

        initializeUserLoginInput();
        confirm.setOnClickListener(unused -> viewModel.submit());
    }

    private void updateBullets(@NonNull Optional<String> formattedBalance) {
        bullets.setText(buildBulletsText(formattedBalance));
    }

    private @NonNull CharSequence buildBulletsText(@NonNull Optional<String> formattedBalance) {
        SpannableStringBuilder builder =  new SpannableStringBuilder().append(SpanUtil.bullet(getString(R.string.DeleteAccountFragment__delete_your_account_info_and_profile_photo)))
                .append("\n")
                .append(SpanUtil.bullet(getString(R.string.DeleteAccountFragment__delete_all_your_messages)));

        if (formattedBalance.isPresent()) {
            builder.append("\n");
            builder.append(SpanUtil.bullet(getString(R.string.DeleteAccountFragment__delete_s_in_your_payments_account, formattedBalance.get())));
        }

        return builder;
    }

    private String reformatText(Editable s) {

        if (TextUtils.isEmpty(s)) {
            return null;
        }

        return s.toString();
    }

    private void initializeUserLoginInput() {
        EditText userLoginInput    = userLogin.getInput();
        String   userLoginModel = viewModel.getUserLogin();

        if (userLoginModel != null) {
            userLogin.setText(userLoginModel);
        } else {
            userLogin.setText("");
        }

        userLoginInput.addTextChangedListener(new AfterTextChanged(this::afterUserLoginChanged));
        userLoginInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        userLoginInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                ViewUtil.hideKeyboard(requireContext(), v);
                viewModel.submit();
                return true;
            }
            return false;
        });
    }

    private void afterUserLoginChanged(@Nullable Editable s) {
        if (userLogin == null) return;
        viewModel.setUserLogin(reformatText(s));
    }

    private void handleEvent(@NonNull DeleteAccountViewModel.EventType eventType) {
        switch (eventType) {
            case NOT_A_MATCH:
                new AlertDialog.Builder(requireContext())
                        .setMessage(R.string.DeleteAccountFragment__the_phone_number)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> dialog.dismiss())
                        .setCancelable(true)
                        .show();
                break;
            case CONFIRM_DELETION:
                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.DeleteAccountFragment__are_you_sure)
                        .setMessage(R.string.DeleteAccountFragment__this_will_delete_your_signal_account)
                        .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
                        .setPositiveButton(R.string.DeleteAccountFragment__delete_account, (dialog, which) -> {
                            dialog.dismiss();
                            deletionProgressDialog = SimpleProgressDialog.show(requireContext());
                            viewModel.deleteAccount();
                        })
                        .setCancelable(true)
                        .show();
                break;
            case SERVER_DELETION_FAILED:
                dismissDeletionProgressDialog();
                showNetworkDeletionFailedDialog();
                break;
            case LOCAL_DATA_DELETION_FAILED:
                dismissDeletionProgressDialog();
                showLocalDataDeletionFailedDialog();
                break;
            default:
                throw new IllegalStateException("Unknown error type: " + eventType);
        }
    }

    private void dismissDeletionProgressDialog() {
        if (deletionProgressDialog != null) {
            deletionProgressDialog.dismiss();
            deletionProgressDialog = null;
        }
    }

    private void showNetworkDeletionFailedDialog() {
        new AlertDialog.Builder(requireContext())
                .setMessage(R.string.DeleteAccountFragment__failed_to_delete_account)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> dialog.dismiss())
                .setCancelable(true)
                .show();
    }

    private void showLocalDataDeletionFailedDialog() {
        new AlertDialog.Builder(requireContext())
                .setMessage(R.string.DeleteAccountFragment__failed_to_delete_local_data)
                .setPositiveButton(R.string.DeleteAccountFragment__launch_app_settings, (dialog, which) -> {
                    Intent settingsIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    settingsIntent.setData(Uri.fromParts("package", requireActivity().getPackageName(), null));
                    startActivity(settingsIntent);
                })
                .setCancelable(false)
                .show();
    }
}
