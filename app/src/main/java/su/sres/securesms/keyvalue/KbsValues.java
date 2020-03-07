package su.sres.securesms.keyvalue;

// import androidx.annotation.Nullable;

// import su.sres.securesms.util.JsonUtils;
// import su.sres.signalservice.api.RegistrationLockData;
// import su.sres.signalservice.internal.contacts.entities.TokenResponse;
// import su.sres.signalservice.internal.registrationpin.PinStretcher;

import androidx.annotation.Nullable;

import java.io.IOException;

import su.sres.signalservice.api.kbs.MasterKey;

public final class KbsValues {

//    private static final String REGISTRATION_LOCK_PREF_V2        = "kbs.registration_lock_v2";
//    private static final String REGISTRATION_LOCK_TOKEN_PREF     = "kbs.registration_lock_token";
//    private static final String REGISTRATION_LOCK_PIN_KEY_2_PREF = "kbs.registration_lock_pin_key_2";
    private static final String REGISTRATION_LOCK_MASTER_KEY     = "kbs.registration_lock_master_key";
//    private static final String REGISTRATION_LOCK_TOKEN_RESPONSE = "kbs.registration_lock_token_response";

    private final KeyValueStore store;

    KbsValues(KeyValueStore store) {
        this.store = store;
    }

//    public void setRegistrationLockMasterKey(@Nullable RegistrationLockData registrationLockData) {

    public void setRegistrationLockMasterKey(byte[] masterKey) {
        KeyValueStore.Writer editor = store.beginWrite();

//        if (registrationLockData == null) {
//            editor.remove(REGISTRATION_LOCK_PREF_V2)
//                    .remove(REGISTRATION_LOCK_TOKEN_RESPONSE)
//                    .remove(REGISTRATION_LOCK_MASTER_KEY)
//                    .remove(REGISTRATION_LOCK_TOKEN_PREF)
//                    .remove(REGISTRATION_LOCK_PIN_KEY_2_PREF);
//        } else {
//            PinStretcher.MasterKey masterKey     = registrationLockData.getMasterKey();
//                     String                 tokenResponse;
//            try {
//                tokenResponse = JsonUtils.toJson(registrationLockData.getTokenResponse());
//            } catch (IOException e) {
//                throw new AssertionError(e);
//            }

//            editor.putBoolean(REGISTRATION_LOCK_PREF_V2, true)
//                    .putString(REGISTRATION_LOCK_TOKEN_RESPONSE, tokenResponse)
//                    .putBlob(REGISTRATION_LOCK_MASTER_KEY, masterKey.getMasterKey())
//                    .putString(REGISTRATION_LOCK_TOKEN_PREF, masterKey.getRegistrationLock())
//                    .putBlob(REGISTRATION_LOCK_PIN_KEY_2_PREF, masterKey.getPinKey2());
//        }

        editor.putBlob(REGISTRATION_LOCK_MASTER_KEY, masterKey);
        editor.commit();
    }

    public @Nullable MasterKey getMasterKey() {
        byte[] blob = store.getBlob(REGISTRATION_LOCK_MASTER_KEY, null);
        if (blob != null) {
            return new MasterKey(blob);
        } else {
            return null;
        }
    }

//    public @Nullable String getRegistrationLockToken() {
//        return store.getString(REGISTRATION_LOCK_TOKEN_PREF, null);
//    }

//    public @Nullable byte[] getRegistrationLockPinKey2() {
//        return store.getBlob(REGISTRATION_LOCK_PIN_KEY_2_PREF, null);
//    }

//    public boolean isV2RegistrationLockEnabled() {
//        return store.getBoolean(REGISTRATION_LOCK_PREF_V2, false);
//    }

//    public @Nullable
//    TokenResponse getRegistrationLockTokenResponse() {
//        String token = store.getString(REGISTRATION_LOCK_TOKEN_RESPONSE, null);
//
//        if (token == null) return null;

//        try {
//            return JsonUtils.fromJson(token, TokenResponse.class);
//        } catch (IOException e) {
//            throw new AssertionError(e);
//        }
 //   }
}