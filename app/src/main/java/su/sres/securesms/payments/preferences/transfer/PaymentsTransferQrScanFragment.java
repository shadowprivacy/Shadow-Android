package su.sres.securesms.payments.preferences.transfer;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import su.sres.core.util.ThreadUtil;
import su.sres.core.util.logging.Log;
import su.sres.securesms.LoggingFragment;
import su.sres.securesms.R;
import su.sres.securesms.components.camera.CameraView;
import su.sres.securesms.payments.MobileCoinPublicAddress;
import su.sres.securesms.qr.ScanningThread;

public final class PaymentsTransferQrScanFragment extends LoggingFragment {

  private static final String TAG = Log.tag(PaymentsTransferQrScanFragment.class);

  private LinearLayout              overlay;
  private CameraView                scannerView;
  private ScanningThread            scanningThread;
  private PaymentsTransferViewModel viewModel;

  public PaymentsTransferQrScanFragment() {
    super(R.layout.payments_transfer_qr_scan_fragment);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    overlay     = view.findViewById(R.id.overlay);
    scannerView = view.findViewById(R.id.scanner);

    if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
      overlay.setOrientation(LinearLayout.HORIZONTAL);
    } else {
      overlay.setOrientation(LinearLayout.VERTICAL);
    }

    viewModel = new ViewModelProvider(Navigation.findNavController(view).getViewModelStoreOwner(R.id.payments_transfer), new PaymentsTransferViewModel.Factory()).get(PaymentsTransferViewModel.class);

    Toolbar toolbar = view.findViewById(R.id.payments_transfer_scan_qr);
    toolbar.setNavigationOnClickListener(v -> Navigation.findNavController(v).popBackStack());
  }

  @Override
  public void onResume() {
    super.onResume();
    scanningThread = new ScanningThread();
    scanningThread.setScanListener(data -> ThreadUtil.runOnMain(() -> {
      try {
        viewModel.postQrData(MobileCoinPublicAddress.fromQr(data).getPaymentAddressBase58());
        Navigation.findNavController(requireView()).navigate(R.id.action_paymentsScanQr_pop);
      } catch (MobileCoinPublicAddress.AddressException e) {
        Log.e(TAG, "Not a valid address");
      }
    }));
    scannerView.onResume();
    scannerView.setPreviewCallback(scanningThread);
    scanningThread.start();
  }

  @Override
  public void onPause() {
    super.onPause();
    scannerView.onPause();
    scanningThread.stopScanning();
  }

  @Override
  public void onConfigurationChanged(@NonNull Configuration newConfiguration) {
    super.onConfigurationChanged(newConfiguration);

    scannerView.onPause();

    if (newConfiguration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
      overlay.setOrientation(LinearLayout.HORIZONTAL);
    } else {
      overlay.setOrientation(LinearLayout.VERTICAL);
    }

    scannerView.onResume();
    scannerView.setPreviewCallback(scanningThread);
  }
}
