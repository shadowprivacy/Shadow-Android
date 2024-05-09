package su.sres.securesms.registration.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProviders;
import androidx.navigation.ActivityNavigator;
// import androidx.navigation.ActivityNavigator;

import su.sres.securesms.LoggingFragment;
import su.sres.securesms.MainActivity;
import su.sres.securesms.R;
import su.sres.core.util.logging.Log;
import su.sres.securesms.profiles.edit.EditProfileActivity;
import su.sres.securesms.registration.viewmodel.RegistrationViewModel;

public final class RegistrationCompleteFragment extends LoggingFragment {

  private static final String TAG = Log.tag(RegistrationCompleteFragment.class);

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState)
  {
    return inflater.inflate(R.layout.fragment_registration_blank, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    FragmentActivity      activity  = requireActivity();
    RegistrationViewModel viewModel = ViewModelProviders.of(activity).get(RegistrationViewModel.class);

    if (!viewModel.isReregister()) {
      final Intent main    = MainActivity.clearTop(activity);
      final Intent profile = EditProfileActivity.getIntentForUserProfile(activity);

      activity.startActivity(chainIntents(profile, main));

    }

    activity.finish();
    ActivityNavigator.applyPopAnimationsToPendingTransition(activity);
  }

  private static Intent chainIntents(@NonNull Intent sourceIntent, @Nullable Intent nextIntent) {
    if (nextIntent != null) sourceIntent.putExtra("next_intent", nextIntent);
    return sourceIntent;
  }
}