package su.sres.securesms.preferences;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import su.sres.core.util.concurrent.SignalExecutors;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.securesms.net.PipeConnectivityListener;
import su.sres.securesms.util.ShadowProxyUtil;
import su.sres.securesms.util.SingleLiveEvent;
import su.sres.securesms.util.TextSecurePreferences;
import su.sres.securesms.util.Util;
import su.sres.signalservice.internal.configuration.ShadowProxy;

import java.util.concurrent.TimeUnit;

public class EditProxyViewModel extends ViewModel {

    private final SingleLiveEvent<Event>                   events;
    private final MutableLiveData<UiState>                 uiState;
    private final MutableLiveData<SaveState>               saveState;
    private final LiveData<PipeConnectivityListener.State> pipeState;

    public EditProxyViewModel() {
        this.events    = new SingleLiveEvent<>();
        this.uiState   = new MutableLiveData<>();
        this.saveState = new MutableLiveData<>(SaveState.IDLE);
        this.pipeState = TextSecurePreferences.getLocalNumber(ApplicationDependencies.getApplication()) == null ? new MutableLiveData<>()
                : ApplicationDependencies.getPipeListener().getState();

        if (SignalStore.proxy().isProxyEnabled()) {
            uiState.setValue(UiState.ALL_ENABLED);
        } else {
            uiState.setValue(UiState.ALL_DISABLED);
        }
    }

    void onToggleProxy(boolean enabled) {
        if (enabled) {
            ShadowProxy currentProxy = SignalStore.proxy().getProxy();

            if (currentProxy != null && !Util.isEmpty(currentProxy.getHost())) {
                ShadowProxyUtil.enableProxy(currentProxy);
            }
            uiState.postValue(UiState.ALL_ENABLED);
        } else {
            ShadowProxyUtil.disableProxy();
            uiState.postValue(UiState.ALL_DISABLED);
        }
    }

    public void onSaveClicked(@NonNull String host) {
        String trueHost = ShadowProxyUtil.convertUserEnteredAddressToHost(host);

        saveState.postValue(SaveState.IN_PROGRESS);

        SignalExecutors.BOUNDED.execute(() -> {
            ShadowProxyUtil.enableProxy(new ShadowProxy(trueHost, 443));

            boolean success = ShadowProxyUtil.testWebsocketConnection(TimeUnit.SECONDS.toMillis(10));

            if (success) {
                events.postValue(Event.PROXY_SUCCESS);
            } else {
                ShadowProxyUtil.disableProxy();
                events.postValue(Event.PROXY_FAILURE);
            }

            saveState.postValue(SaveState.IDLE);
        });
    }

    @NonNull LiveData<UiState> getUiState() {
        return uiState;
    }

    public @NonNull LiveData<Event> getEvents() {
        return events;
    }

    @NonNull LiveData<PipeConnectivityListener.State> getProxyState() {
        return pipeState;
    }

    public @NonNull LiveData<SaveState> getSaveState() {
        return saveState;
    }

    enum UiState {
        ALL_DISABLED, ALL_ENABLED
    }

    public enum Event {
        PROXY_SUCCESS, PROXY_FAILURE
    }

    public enum SaveState {
        IDLE, IN_PROGRESS
    }
}