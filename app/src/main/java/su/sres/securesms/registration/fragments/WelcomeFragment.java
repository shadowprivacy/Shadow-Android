package su.sres.securesms.registration.fragments;

import static su.sres.securesms.registration.fragments.RegistrationViewDelegate.setDebugLogSubmitMultiTapView;
import static su.sres.securesms.util.CircularProgressButtonUtil.cancelSpinning;
import static su.sres.securesms.util.CircularProgressButtonUtil.setSpinning;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProviders;
import androidx.navigation.ActivityNavigator;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;

import com.dd.CircularProgressButton;

import org.greenrobot.eventbus.EventBus;

import su.sres.devicetransfer.DeviceToDeviceTransferService;
import su.sres.devicetransfer.TransferStatus;
import su.sres.securesms.LoggingFragment;
import su.sres.securesms.R;
import su.sres.core.util.logging.Log;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.securesms.permissions.Permissions;
import su.sres.securesms.registration.viewmodel.RegistrationViewModel;
import su.sres.securesms.util.BackupUtil;
import su.sres.securesms.util.TextSecurePreferences;

public final class WelcomeFragment extends LoggingFragment {

  private static final String TAG = Log.tag(WelcomeFragment.class);

  private static final            String[] PERMISSIONS        = {
      Manifest.permission.WRITE_CONTACTS,
      Manifest.permission.READ_CONTACTS,
      Manifest.permission.WRITE_EXTERNAL_STORAGE,
      Manifest.permission.READ_EXTERNAL_STORAGE,
      Manifest.permission.READ_PHONE_STATE };
  @RequiresApi(26)
  private static final            String[] PERMISSIONS_API_26 = { Manifest.permission.WRITE_CONTACTS,
                                                                  Manifest.permission.READ_CONTACTS,
                                                                  Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                                                  Manifest.permission.READ_EXTERNAL_STORAGE,
                                                                  Manifest.permission.READ_PHONE_STATE };
  @RequiresApi(26)
  private static final            String[] PERMISSIONS_API_29 = {
      Manifest.permission.WRITE_CONTACTS,
      Manifest.permission.READ_CONTACTS,
      Manifest.permission.READ_PHONE_STATE };
  private static final @StringRes int      RATIONALE          = R.string.RegistrationActivity_signal_needs_access_to_your_contacts_and_media_in_order_to_connect_with_friends;
  private static final @StringRes
  int RATIONALE_API_29 = R.string.RegistrationActivity_signal_needs_access_to_your_contacts_in_order_to_connect_with_friends;
  private static final int[] HEADERS        = { R.drawable.ic_contacts_white_48dp, R.drawable.ic_folder_white_48dp };
  private static final int[] HEADERS_API_29 = { R.drawable.ic_contacts_white_48dp };

  private CircularProgressButton continueButton;
  private RegistrationViewModel  viewModel;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_registration_welcome, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    viewModel = ViewModelProviders.of(requireActivity()).get(RegistrationViewModel.class);

    if (viewModel.isReregister()) {
      if (viewModel.hasRestoreFlowBeenShown()) {
        Log.i(TAG, "We've come back to the home fragment on a restore, user must be backing out");
        if (!Navigation.findNavController(view).popBackStack()) {
          FragmentActivity activity = requireActivity();
          activity.finish();
          ActivityNavigator.applyPopAnimationsToPendingTransition(activity);
        }
        return;
      }

      Log.i(TAG, "Skipping restore because this is a reregistration.");
      viewModel.setWelcomeSkippedOnRestore();
      Navigation.findNavController(view)
                .navigate(WelcomeFragmentDirections.actionSkipRestore());

    } else {

      setDebugLogSubmitMultiTapView(view.findViewById(R.id.image));
      setDebugLogSubmitMultiTapView(view.findViewById(R.id.title));

      continueButton = view.findViewById(R.id.welcome_continue_button);
      continueButton.setOnClickListener(this::continueClicked);

      Button restoreFromBackup = view.findViewById(R.id.welcome_transfer_or_restore);
      restoreFromBackup.setOnClickListener(this::restoreFromBackupClicked);

      if (!canUserSelectBackup()) {
        restoreFromBackup.setText(R.string.registration_activity__transfer_account);
      }
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

  @Override
  public void onResume() {
    super.onResume();
    if (EventBus.getDefault().getStickyEvent(TransferStatus.class) != null) {
      Log.i(TAG, "Found existing transferStatus, redirect to transfer flow");
      NavHostFragment.findNavController(this).navigate(R.id.action_welcomeFragment_to_deviceTransferSetup);
    } else {
      DeviceToDeviceTransferService.stop(requireContext());
    }
  }

  private void continueClicked(@NonNull View view) {
    boolean isUserSelectionRequired = BackupUtil.isUserSelectionRequired(requireContext());

    Permissions.with(this)
               .request(getContinuePermissions(isUserSelectionRequired))
               .ifNecessary()
               .withRationaleDialog(getString(getContinueRationale(isUserSelectionRequired)), getContinueHeaders(isUserSelectionRequired))
               .onAnyResult(() -> gatherInformationAndContinue(continueButton))
               .execute();
  }

  private void restoreFromBackupClicked(@NonNull View view) {
    boolean isUserSelectionRequired = BackupUtil.isUserSelectionRequired(requireContext());
    Permissions.with(this)
               .request(getContinuePermissions(isUserSelectionRequired))
               .ifNecessary()
               .withRationaleDialog(getString(getContinueRationale(isUserSelectionRequired)), getContinueHeaders(isUserSelectionRequired))
               .onAnyResult(() -> gatherInformationAndChooseBackup(continueButton))
               .execute();
  }

  private void gatherInformationAndContinue(@NonNull View view) {
    setSpinning(continueButton);

    RestoreBackupFragment.searchForBackup(backup -> {
      Context context = getContext();
      if (context == null) {
        Log.i(TAG, "No context on fragment, must have navigated away.");
        return;
      }

      TextSecurePreferences.setHasSeenWelcomeScreen(requireContext(), true);

      cancelSpinning(continueButton);

      if (backup == null) {
        Log.i(TAG, "Skipping backup. No backup found, or no permission to look.");
        Navigation.findNavController(view)
                  .navigate(WelcomeFragmentDirections.actionSkipRestore());
      } else {
        Navigation.findNavController(view)
                  .navigate(WelcomeFragmentDirections.actionRestore());
      }
    });
  }

  private void gatherInformationAndChooseBackup(@NonNull View view) {
    TextSecurePreferences.setHasSeenWelcomeScreen(requireContext(), true);

    Navigation.findNavController(view)
              .navigate(WelcomeFragmentDirections.actionTransferOrRestore());
  }

  private boolean canUserSelectBackup() {
    return BackupUtil.isUserSelectionRequired(requireContext()) &&
           !viewModel.isReregister() &&
           !SignalStore.settings().isBackupEnabled();
  }

  @SuppressLint("NewApi")
  private static String[] getContinuePermissions(boolean isUserSelectionRequired) {
    if (isUserSelectionRequired) {
      return PERMISSIONS_API_29;
    } else if (Build.VERSION.SDK_INT >= 26) {
      return PERMISSIONS_API_26;
    } else {
      return PERMISSIONS;
    }
  }

  private static @StringRes int getContinueRationale(boolean isUserSelectionRequired) {
    return isUserSelectionRequired ? RATIONALE_API_29 : RATIONALE;
  }

  private static int[] getContinueHeaders(boolean isUserSelectionRequired) {
    return isUserSelectionRequired ? HEADERS_API_29 : HEADERS;
  }
}