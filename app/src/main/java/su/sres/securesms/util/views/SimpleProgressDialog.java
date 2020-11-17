package su.sres.securesms.util.views;

import android.content.Context;

import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import java.util.concurrent.atomic.AtomicReference;

import su.sres.securesms.R;
import su.sres.securesms.logging.Log;
import su.sres.securesms.util.Util;

/**
 * Helper class to show a fullscreen blocking indeterminate progress dialog.
 */
public final class SimpleProgressDialog {

    private static final String TAG = Log.tag(SimpleProgressDialog.class);

    private SimpleProgressDialog() {}

    @MainThread
    public static @NonNull AlertDialog show(@NonNull Context context) {
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(R.layout.progress_dialog)
                .setCancelable(false)
                .create();
        dialog.show();
        dialog.getWindow().setLayout(context.getResources().getDimensionPixelSize(R.dimen.progress_dialog_size),
                context.getResources().getDimensionPixelSize(R.dimen.progress_dialog_size));

        return dialog;
    }

    @AnyThread
    public static @NonNull DismissibleDialog showDelayed(@NonNull Context context) {
        return showDelayed(context, 300);
    }

    /**
     * Shows the dialog after {@param delayMs} ms.
     * <p>
     * To dismiss, call {@link DismissibleDialog#dismiss()} on the result. If dismiss is called before
     * the delay has elapsed, the dialog will not show at all.
     * <p>
     * Dismiss can be called on any thread.
     */
    @AnyThread
    public static @NonNull DismissibleDialog showDelayed(@NonNull Context context, int delayMs) {
        AtomicReference<AlertDialog> dialogAtomicReference = new AtomicReference<>();

        Runnable showRunnable = () -> {
            Log.i(TAG, "Taking some time. Showing a progress dialog.");
            dialogAtomicReference.set(show(context));
        };

        Util.runOnMainDelayed(showRunnable, delayMs);

        return () -> {
            Util.cancelRunnableOnMain(showRunnable);
            Util.runOnMain(() -> {
                AlertDialog alertDialog = dialogAtomicReference.getAndSet(null);
                if (alertDialog != null) {
                    alertDialog.dismiss();
                }
            });
        };
    }

    public interface DismissibleDialog {
        @AnyThread
        void dismiss();
    }
}