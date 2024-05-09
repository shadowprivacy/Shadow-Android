package su.sres.securesms.registration.fragments;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProviders;
import androidx.navigation.Navigation;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.IOException;

import su.sres.securesms.R;

import su.sres.securesms.events.ServerCertErrorEvent;
import su.sres.core.util.logging.Log;
import su.sres.securesms.registration.viewmodel.RegistrationViewModel;
import su.sres.securesms.util.FeatureFlags;
import su.sres.securesms.util.concurrent.SimpleTask;


public final class EnterCodeFragment extends BaseEnterCodeFragment<RegistrationViewModel> {

  private static final String TAG = Log.tag(EnterCodeFragment.class);

  public EnterCodeFragment() {
    super(R.layout.fragment_registration_enter_code);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    EventBus.getDefault().register(this);
  }

  protected @NonNull RegistrationViewModel getViewModel() {
    return ViewModelProviders.of(requireActivity()).get(RegistrationViewModel.class);
  }

  @Override
  protected void handleSuccessfulVerify() {
    SimpleTask.run(() -> {
      long startTime = System.currentTimeMillis();
      try {
        FeatureFlags.refreshSync();
        Log.i(TAG, "Took " + (System.currentTimeMillis() - startTime) + " ms to get feature flags.");
      } catch (IOException e) {
        Log.w(TAG, "Failed to refresh flags after " + (System.currentTimeMillis() - startTime) + " ms.", e);
      }
      return null;
    }, none -> displaySuccess(() -> Navigation.findNavController(requireView()).navigate(EnterCodeFragmentDirections.actionSuccessfulRegistration())));
  }

  @Override
  public void onResume() {
    super.onResume();

    header.setText(requireContext().getString(R.string.RegistrationActivity_enter_the_code_we_sent_to_s));
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    EventBus.getDefault().unregister(this);
  }

  // captcha off
    /* protected void navigateToCaptcha() {
        NavHostFragment.findNavController(this).navigate(EnterCodeFragmentDirections.actionRequestCaptcha());
    } */

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onEventServerCertError(ServerCertErrorEvent event) {
    Toast.makeText(getActivity(), event.message, Toast.LENGTH_LONG).show();
  }
}