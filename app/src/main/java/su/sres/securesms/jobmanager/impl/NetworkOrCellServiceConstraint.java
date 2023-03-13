package su.sres.securesms.jobmanager.impl;

import android.app.Application;
import android.app.job.JobInfo;
import androidx.annotation.NonNull;

import su.sres.securesms.jobmanager.Constraint;
import su.sres.securesms.util.TextSecurePreferences;

public class NetworkOrCellServiceConstraint implements Constraint {

    public static final String KEY        = "NetworkOrCellServiceConstraint";
    public static final String LEGACY_KEY = "CellServiceConstraint";

    private final Application       application;
    private final NetworkConstraint networkConstraint;

    private NetworkOrCellServiceConstraint(@NonNull Application application) {
        this.application       = application;
        this.networkConstraint = new NetworkConstraint.Factory(application).create();
    }

    @Override
    public @NonNull String getFactoryKey() {
        return KEY;
    }

    @Override
    public boolean isMet() {
            return networkConstraint.isMet();
    }

    @Override
    public void applyToJobInfo(@NonNull JobInfo.Builder jobInfoBuilder) {
    }

    public static class Factory implements Constraint.Factory<NetworkOrCellServiceConstraint> {

        private final Application application;

        public Factory(@NonNull Application application) {
            this.application = application;
        }

        @Override
        public NetworkOrCellServiceConstraint create() {
            return new NetworkOrCellServiceConstraint(application);
        }
    }
}