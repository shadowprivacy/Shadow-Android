package su.sres.securesms.registration.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.ActivityNavigator;
// import androidx.navigation.ActivityNavigator;

import java.util.Arrays;

import su.sres.securesms.LoggingFragment;
import su.sres.securesms.MainActivity;
import su.sres.securesms.R;
import su.sres.core.util.logging.Log;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobs.MultiDeviceProfileContentUpdateJob;
import su.sres.securesms.jobs.MultiDeviceProfileKeyUpdateJob;
import su.sres.securesms.jobs.ProfileUploadJob;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.securesms.profiles.AvatarHelper;
import su.sres.securesms.profiles.edit.EditProfileActivity;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.registration.RegistrationUtil;
import su.sres.securesms.registration.viewmodel.RegistrationViewModel;

public final class RegistrationCompleteFragment extends LoggingFragment {

  private static final String TAG = Log.tag(RegistrationCompleteFragment.class);

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_registration_blank, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    FragmentActivity      activity  = requireActivity();
    RegistrationViewModel viewModel = new ViewModelProvider(activity).get(RegistrationViewModel.class);

    if (!viewModel.isReregister()) {
      boolean needsProfile = Recipient.self().getProfileName().isEmpty() || !AvatarHelper.hasAvatar(activity, Recipient.self().getId());
      // boolean needsPin     = !SignalStore.kbsValues().hasPin();

      Log.i(TAG, "Pin restore flow not required." +
                 " profile name: " + Recipient.self().getProfileName().isEmpty() +
                 " profile avatar: " + !AvatarHelper.hasAvatar(activity, Recipient.self().getId()));
      // +
      //           " needsPin:" + needsPin);

      Intent startIntent = MainActivity.clearTop(activity);

      /* if (needsPin) {
        startIntent = chainIntents(CreateKbsPinActivity.getIntentForPinCreate(requireContext()), startIntent);
      } */

      if (needsProfile) {
        startIntent = chainIntents(EditProfileActivity.getIntentForUserProfile(activity), startIntent);
      }

      if (!needsProfile
          // && !needsPin
      ) {
        ApplicationDependencies.getJobManager()
                               .startChain(new ProfileUploadJob())
                               .then(Arrays.asList(new MultiDeviceProfileKeyUpdateJob(), new MultiDeviceProfileContentUpdateJob()))
                               .enqueue();

        RegistrationUtil.maybeMarkRegistrationComplete(requireContext());
      }

      activity.startActivity(startIntent);
    }

    activity.finish();
    ActivityNavigator.applyPopAnimationsToPendingTransition(activity);
  }

  private static @NonNull Intent chainIntents(@NonNull Intent sourceIntent, @NonNull Intent nextIntent) {
    sourceIntent.putExtra("next_intent", nextIntent);
    return sourceIntent;
  }
}