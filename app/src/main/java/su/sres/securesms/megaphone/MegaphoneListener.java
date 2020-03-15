package su.sres.securesms.megaphone;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

public interface MegaphoneListener {
    /**
     * When a megaphone wants to navigate to a specific intent.
     */
    void onMegaphoneNavigationRequested(@NonNull Intent intent);

    /**
     * When a megaphone wants to navigate to a specific intent for a request code.
     */
    void onMegaphoneNavigationRequested(@NonNull Intent intent, int requestCode);

    /**
     * When a megaphone wants to show a toast/snackbar.
     */
    void onMegaphoneToastRequested(@NonNull String string);

    /**
     * When a megaphone has been snoozed via "remind me later" or a similar option.
     */
    void onMegaphoneSnooze(@NonNull Megaphone megaphone);

    /**
     * Called when a megaphone completed its goal.
     */
    void onMegaphoneCompleted(@NonNull Megaphone  megaphone);
}