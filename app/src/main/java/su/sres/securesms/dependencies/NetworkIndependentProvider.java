package su.sres.securesms.dependencies;

import android.app.Application;

import androidx.annotation.NonNull;

import su.sres.securesms.keyvalue.KeyValueStore;
import su.sres.securesms.shakereport.ShakeToReport;

// Here goes all the stuff that must be initialized prior to the service URL having been set
public class NetworkIndependentProvider implements ApplicationDependencies.NetworkIndependentProvider {

    private final Application                context;

    public NetworkIndependentProvider(@NonNull Application context) {
        this.context       = context;
    }

    public @NonNull
    KeyValueStore provideKeyValueStore() {
        return new KeyValueStore(context);
    }

    @Override
    public @NonNull ShakeToReport provideShakeToReport() {
        return new ShakeToReport(context);
    }

}