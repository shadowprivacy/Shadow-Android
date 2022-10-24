package su.sres.securesms.megaphone;

import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;

import su.sres.securesms.PassphraseRequiredActivity;
import su.sres.securesms.R;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.util.DynamicNoActionBarTheme;
import su.sres.securesms.util.DynamicTheme;
import su.sres.securesms.util.PlayStoreUtil;
import su.sres.securesms.util.Util;

/**
 * Shown when a users build fully expires. Controlled by {@link Megaphones.Event#CLIENT_DEPRECATED}.
 */
public class ClientDeprecatedActivity extends PassphraseRequiredActivity {

    private final DynamicTheme theme = new DynamicNoActionBarTheme();

    @Override
    protected void onCreate(Bundle savedInstanceState, boolean ready) {
        setContentView(R.layout.client_deprecated_activity);

        findViewById(R.id.client_deprecated_update_button).setOnClickListener(v -> onUpdateClicked());
        findViewById(R.id.client_deprecated_dont_update_button).setOnClickListener(v -> onDontUpdateClicked());
    }

    @Override
    protected void onPreCreate() {
        theme.onCreate(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        theme.onResume(this);
    }

    @Override
    public void onBackPressed() {
        // Disabled
    }

    private void onUpdateClicked() {
        PlayStoreUtil.openPlayStoreOrOurApkDownloadPage(this);
    }

    private void onDontUpdateClicked() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.ClientDeprecatedActivity_warning)
                .setMessage(R.string.ClientDeprecatedActivity_your_version_of_signal_has_expired_you_can_view_your_message_history)
                .setPositiveButton(R.string.ClientDeprecatedActivity_dont_update, (dialog, which) -> {
                    ApplicationDependencies.getMegaphoneRepository().markFinished(Megaphones.Event.CLIENT_DEPRECATED, () -> {
                        Util.runOnMain(this::finish);
                    });
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
                .show();
    }
}
