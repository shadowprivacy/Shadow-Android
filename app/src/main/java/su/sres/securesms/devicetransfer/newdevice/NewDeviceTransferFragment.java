package su.sres.securesms.devicetransfer.newdevice;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.fragment.NavHostFragment;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import su.sres.devicetransfer.DeviceToDeviceTransferService;
import su.sres.securesms.R;
import su.sres.securesms.devicetransfer.DeviceTransferFragment;

/**
 * Shows transfer progress on the new device. Most logic is in {@link DeviceTransferFragment}
 * and it delegates to this class for strings, navigation, and updating progress.
 */
public final class NewDeviceTransferFragment extends DeviceTransferFragment {

    private final ServerTaskListener serverTaskListener = new ServerTaskListener();

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        EventBus.getDefault().register(serverTaskListener);
    }

    @Override
    public void onDestroyView() {
        EventBus.getDefault().unregister(serverTaskListener);
        super.onDestroyView();
    }

    @Override
    protected void navigateToRestartTransfer() {
        NavHostFragment.findNavController(this).navigate(R.id.action_newDeviceTransfer_to_newDeviceTransferInstructions);
    }

    @Override
    protected void navigateAwayFromTransfer() {
        EventBus.getDefault().unregister(serverTaskListener);
        NavHostFragment.findNavController(this)
                .navigate(R.id.action_restart_to_welcomeFragment);
    }

    @Override
    protected void navigateToTransferComplete() {
        NavHostFragment.findNavController(this).navigate(R.id.action_newDeviceTransfer_to_newDeviceTransferComplete);
    }

    private class ServerTaskListener {
        @Subscribe(threadMode = ThreadMode.MAIN)
        public void onEventMainThread(@NonNull NewDeviceServerTask.Status event) {
            status.setText(getString(R.string.DeviceTransfer__d_messages_so_far, event.getMessageCount()));
            switch (event.getState()) {
                case IN_PROGRESS:
                    break;
                case SUCCESS:
                    transferFinished = true;
                    DeviceToDeviceTransferService.stop(requireContext());
                    navigateToTransferComplete();
                    break;
                case FAILURE_VERSION_DOWNGRADE:
                    abort(R.string.NewDeviceTransfer__cannot_transfer_from_a_newer_version_of_signal);
                    break;
                case FAILURE_UNKNOWN:
                    abort();
                    break;
            }
        }
    }
}
