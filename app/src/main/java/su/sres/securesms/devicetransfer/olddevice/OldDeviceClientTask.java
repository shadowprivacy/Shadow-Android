package su.sres.securesms.devicetransfer.olddevice;

import android.content.Context;

import androidx.annotation.NonNull;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import su.sres.core.util.logging.Log;
import su.sres.devicetransfer.ClientTask;
import su.sres.securesms.backup.FullBackupBase;
import su.sres.securesms.backup.FullBackupExporter;
import su.sres.securesms.crypto.AttachmentSecretProvider;
import su.sres.securesms.database.ShadowDatabase;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.securesms.net.DeviceTransferBlockingInterceptor;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Create the backup stream of the old device and sends it over the wire via the output stream.
 * Used in conjunction with {@link su.sres.devicetransfer.DeviceToDeviceTransferService}.
 */
final class OldDeviceClientTask implements ClientTask {

  private static final String TAG = Log.tag(OldDeviceClientTask.class);

  private static final long PROGRESS_UPDATE_THROTTLE = 250;

  private long lastProgressUpdate = 0;

  @Override
  public void run(@NonNull Context context, @NonNull OutputStream outputStream) throws IOException {
    DeviceTransferBlockingInterceptor.getInstance().blockNetwork();

    long start = System.currentTimeMillis();

    EventBus.getDefault().register(this);
    try {
      FullBackupExporter.transfer(context,
                                  AttachmentSecretProvider.getInstance(context).getOrCreateAttachmentSecret(),
                                  ShadowDatabase.getBackupDatabase(),
                                  outputStream,
                                  "deadbeef");
    } catch (Exception e) {
      DeviceTransferBlockingInterceptor.getInstance().unblockNetwork();
      throw e;
    } finally {
      EventBus.getDefault().unregister(this);
    }

    long end = System.currentTimeMillis();
    Log.i(TAG, "Sending took: " + (end - start));
  }

  @Subscribe(threadMode = ThreadMode.POSTING)
  public void onEvent(FullBackupBase.BackupEvent event) {
    if (event.getType() == FullBackupBase.BackupEvent.Type.PROGRESS) {
      if (System.currentTimeMillis() > lastProgressUpdate + PROGRESS_UPDATE_THROTTLE) {
        EventBus.getDefault().post(new Status(event.getCount(), false));
        lastProgressUpdate = System.currentTimeMillis();
      }
    }
  }

  @Override
  public void success() {
    SignalStore.misc().markOldDeviceTransferLocked();
    EventBus.getDefault().post(new Status(0, true));
  }

  public static final class Status {
    private final long    messages;
    private final boolean done;

    public Status(long messages, boolean done) {
      this.messages = messages;
      this.done     = done;
    }

    public long getMessageCount() {
      return messages;
    }

    public boolean isDone() {
      return done;
    }
  }
}
