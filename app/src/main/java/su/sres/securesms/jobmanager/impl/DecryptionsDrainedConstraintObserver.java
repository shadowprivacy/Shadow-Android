package su.sres.securesms.jobmanager.impl;

import androidx.annotation.NonNull;

import su.sres.core.util.logging.Log;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobmanager.ConstraintObserver;

/**
 * An observer for {@link DecryptionsDrainedConstraint}. Will fire when the websocket is drained and
 * the relevant decryptions have finished.
 */
public class DecryptionsDrainedConstraintObserver implements ConstraintObserver {

    private static final String REASON = Log.tag(DecryptionsDrainedConstraintObserver.class);
    @Override
    public void register(@NonNull Notifier notifier) {
        ApplicationDependencies.getIncomingMessageObserver().addDecryptionDrainedListener(() -> {
            notifier.onConstraintMet(REASON);
        });
    }
}