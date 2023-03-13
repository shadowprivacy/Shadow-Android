package su.sres.securesms.util;

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import su.sres.core.util.logging.Log;

public final class NetworkConnectionStateListener extends ConnectivityManager.NetworkCallback
        implements DefaultLifecycleObserver {
    private static final String TAG = Log.tag(NetworkConnectionStateListener.class);

    private final Callback callback;
    private final Debouncer debouncer = new Debouncer(1000);
    private final ConnectivityManager connManager;

    public NetworkConnectionStateListener(@NonNull LifecycleOwner lifecycleOwner, @NonNull Callback callback, ConnectivityManager connManager) {
        this.callback = callback;
        lifecycleOwner.getLifecycle().addObserver(this);
        this.connManager = connManager;
    }

    @Override
    public void onAvailable(@NonNull Network network) {
        Log.i(TAG, "Internet connection available");
        debouncer.clear();
        callback.onConnectionPresent();
    }

    @Override
    public void onLost(@NonNull Network network) {
        Log.w(TAG, "Internet connection lost");
        debouncer.publish(callback::onNoConnectionPresent);
    }

    public interface Callback {
        void onNoConnectionPresent();

        void onConnectionPresent();
    }

    @Override
    public void onResume(@NonNull LifecycleOwner owner) {

        NetworkCapabilities caps = connManager.getNetworkCapabilities(connManager.getActiveNetwork());

        if (caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
            Log.i(TAG, "Internet connection normal");
            callback.onConnectionPresent();
        } else {
            Log.w(TAG, "No internet connection");
            callback.onNoConnectionPresent();
        }

        connManager.registerNetworkCallback(new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                .build(), this);
        Log.i(TAG, "Listening to internet access state changes");
    }

    @Override
    public void onPause(@NonNull LifecycleOwner owner) {
        connManager.unregisterNetworkCallback(this);
        Log.i(TAG, "Stopped listening to internet access state changes");
    }
}
