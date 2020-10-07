package su.sres.securesms.keyvalue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.security.SecureRandom;

import su.sres.signalservice.api.kbs.MasterKey;

public final class KbsValues extends SignalStoreValues {

    private static final String MASTER_KEY          = "kbs.registration_lock_master_key";

    KbsValues(KeyValueStore store) {
        super(store);
    }

    @Override
    void onFirstEverAppLaunch() {
    }

    /** Should only be set by {@link su.sres.securesms.pin.PinState}. */
//    public synchronized void setKbsMasterKey(@NonNull KbsPinData pinData, @NonNull String localPinHash) {

        public synchronized void setKbsMasterKey(MasterKey masterKey) {
//            MasterKey masterKey     = registrationLockData.getMasterKey();
//            String    tokenResponse;
//            try {
//                tokenResponse = JsonUtils.toJson(pinData.getTokenResponse());
//            } catch (IOException e) {
//                throw new AssertionError(e);

            getStore().beginWrite()
                      .putBlob(MASTER_KEY, masterKey.serialize())
                      .commit();
    }

        /**
         * Finds or creates the master key. Therefore this will always return a master key whether backed
         * up or not.
         * <p>
         * If you only want a key when it's backed up, use {@link #getPinBackedMasterKey()}.
         */
        public synchronized @NonNull MasterKey getOrCreateMasterKey() {
            byte[] blob = getStore().getBlob(MASTER_KEY, null);

            if (blob == null) {
                getStore().beginWrite()
                        .putBlob(MASTER_KEY, MasterKey.createNew(new SecureRandom()).serialize())
                        .commit();
                blob = getBlob(MASTER_KEY, null);
    }

            return new MasterKey(blob);
        }

    private synchronized @Nullable MasterKey getMasterKey() {
        byte[] blob = getBlob(MASTER_KEY, null);
        return blob != null ? new MasterKey(blob) : null;
    }
}