package su.sres.signalservice.api.messages.multidevice;

import org.whispersystems.libsignal.util.guava.Optional;
import su.sres.signalservice.api.storage.StorageKey;

public class KeysMessage {

    private final Optional<StorageKey> storageService;

    public KeysMessage(Optional<StorageKey> storageService) {
        this.storageService = storageService;
    }

    public Optional<StorageKey> getStorageService() {
        return storageService;
    }
}