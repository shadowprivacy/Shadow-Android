package su.sres.securesms.jobmanager.impl;

import androidx.annotation.NonNull;

import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobmanager.ConstraintObserver;

/**
 * An observer for {@link WebsocketDrainedConstraint}. Will fire when the
 * {@link su.sres.securesms.messages.InitialMessageRetriever} is caught up.
 */
public class WebsocketDrainedConstraintObserver implements ConstraintObserver {

    private static final String REASON = WebsocketDrainedConstraintObserver.class.getSimpleName();

    @Override
    public void register(@NonNull Notifier notifier) {
        ApplicationDependencies.getInitialMessageRetriever().addListener(() -> {
            notifier.onConstraintMet(REASON);
        });
    }
}