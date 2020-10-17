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

    private volatile Notifier notifier;

    public WebsocketDrainedConstraintObserver() {
        ApplicationDependencies.getInitialMessageRetriever().addListener(() -> {
            if (notifier != null) {
                notifier.onConstraintMet(REASON);
            }
        });
    }

    @Override
    public void register(@NonNull Notifier notifier) {
        this.notifier = notifier;
    }
}