package su.sres.securesms.dependencies;

import android.app.Application;

import androidx.annotation.NonNull;

import su.sres.securesms.keyvalue.KeyValueStore;

public class NetworkIndependentProvider implements ApplicationDependencies.NetworkIndependentProvider {

    private final Application                context;

    public NetworkIndependentProvider(@NonNull Application context) {
        this.context       = context;
    }

    public @NonNull
    KeyValueStore provideKeyValueStore() {
        return new KeyValueStore(context);
    }

}