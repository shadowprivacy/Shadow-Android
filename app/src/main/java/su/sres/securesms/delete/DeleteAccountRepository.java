package su.sres.securesms.delete;

import androidx.annotation.NonNull;

import su.sres.core.util.concurrent.SignalExecutors;
import su.sres.core.util.logging.Log;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.util.ServiceUtil;

import java.io.IOException;

class DeleteAccountRepository {
    private static final String TAG = Log.tag(DeleteAccountRepository.class);

    void deleteAccount(@NonNull Runnable onFailureToDeleteFromService,
                       @NonNull Runnable onFailureToDeleteLocalData)
    {
        SignalExecutors.BOUNDED.execute(() -> {

            Log.i(TAG, "deleteAccount: attempting to delete account from server...");

            try {
                ApplicationDependencies.getSignalServiceAccountManager().deleteAccount();
            } catch (IOException e) {
                Log.w(TAG, "deleteAccount: failed to delete account from the Shadow service", e);
                onFailureToDeleteFromService.run();
                return;
            }

            Log.i(TAG, "deleteAccount: successfully removed account from server");
            Log.i(TAG, "deleteAccount: attempting to delete user data and close process...");

            if (!ServiceUtil.getActivityManager(ApplicationDependencies.getApplication()).clearApplicationUserData()) {
                Log.w(TAG, "deleteAccount: failed to delete user data");
                onFailureToDeleteLocalData.run();
            }
        });
    }
}
