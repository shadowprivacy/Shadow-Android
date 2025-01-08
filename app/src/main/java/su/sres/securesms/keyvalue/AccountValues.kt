package su.sres.securesms.keyvalue

import android.content.Context
import androidx.annotation.VisibleForTesting
import su.sres.core.util.logging.Log
import su.sres.securesms.crypto.ProfileKeyUtil
import su.sres.securesms.database.ShadowDatabase
import su.sres.securesms.dependencies.ApplicationDependencies
import su.sres.securesms.recipients.Recipient
import su.sres.securesms.util.TextSecurePreferences
import su.sres.securesms.util.Util
import su.sres.signalservice.api.push.ACI
import su.sres.signalservice.api.push.PNI

internal class AccountValues internal constructor(store: KeyValueStore) : SignalStoreValues(store) {

  companion object {
    private val TAG = Log.tag(AccountValues::class.java)
    private const val KEY_ACI = "account.aci"
    private const val KEY_PNI = "account.pni"
    private const val KEY_SERVICE_PASSWORD = "account.service_password"
    private const val KEY_IS_REGISTERED = "account.is_registered"
    private const val KEY_REGISTRATION_ID = "account.registration_id"
    private const val KEY_FCM_ENABLED = "account.fcm_enabled"
    private const val KEY_FCM_TOKEN = "account.fcm_token"
    private const val KEY_FCM_TOKEN_VERSION = "account.fcm_token_version"
    private const val KEY_FCM_TOKEN_LAST_SET_TIME = "account.fcm_token_last_set_time"

    @VisibleForTesting
    const val KEY_USER_LOGIN = "account.user_login"
  }

  init {
    if (!store.containsKey(KEY_ACI)) {
      migrateFromSharedPrefs(ApplicationDependencies.getApplication())
    }
  }

  public override fun onFirstEverAppLaunch() = Unit

  public override fun getKeysToIncludeInBackup(): List<String> {
    return emptyList()
  }

  /** The local user's [ACI]. */
  val aci: ACI?
    get() = ACI.parseOrNull(getString(KEY_ACI, null))

  fun setAci(aci: ACI) {
    putString(KEY_ACI, aci.toString())
  }

  /** The local user's [PNI]. */
  val pni: PNI?
    get() = PNI.parseOrNull(getString(KEY_PNI, null))

  fun setPni(pni: PNI) {
    putString(KEY_PNI, pni.toString())
  }

  /** The local user's login. */
  val userLogin: String?
    get() = getString(KEY_USER_LOGIN, null)

  fun setUserLogin(userLogin: String) {
    putString(KEY_USER_LOGIN, userLogin)
  }

  /** The password for communicating with the Signal service. */
  val servicePassword: String?
    get() = getString(KEY_SERVICE_PASSWORD, null)

  fun setServicePassword(servicePassword: String) {
    putString(KEY_SERVICE_PASSWORD, servicePassword)
  }

  /** A randomly-generated value that represents this registration instance. Helps the server know if you reinstalled. */
  var registrationId: Int
    get() = getInteger(KEY_REGISTRATION_ID, 0)
    set(value) = putInteger(KEY_REGISTRATION_ID, value)

  /** Indicates whether the user has the ability to receive FCM messages. Largely coupled to whether they have Play Service. */
  var fcmEnabled: Boolean
    @JvmName("isFcmEnabled")
    get() = getBoolean(KEY_FCM_ENABLED, false)
    set(value) = putBoolean(KEY_FCM_ENABLED, value)

  /** The FCM token, which allows the server to send us FCM messages. */
  var fcmToken: String?
    get() {
      val tokenVersion: Int = getInteger(KEY_FCM_TOKEN_VERSION, 0)
      return if (tokenVersion == Util.getCanonicalVersionCode()) {
        getString(KEY_FCM_TOKEN, null)
      } else {
        null
      }
    }
    set(value) {
      store.beginWrite()
        .putString(KEY_FCM_TOKEN, value)
        .putInteger(KEY_FCM_TOKEN_VERSION, Util.getCanonicalVersionCode())
        .putLong(KEY_FCM_TOKEN_LAST_SET_TIME, System.currentTimeMillis())
        .apply()
    }

  /** When we last set the [fcmToken] */
  val fcmTokenLastSetTime: Long
    get() = getLong(KEY_FCM_TOKEN_LAST_SET_TIME, 0)

  /** Whether or not the user is registered with the Signal service. */
  val isRegistered: Boolean
    get() = getBoolean(KEY_IS_REGISTERED, false)

  fun setRegistered(registered: Boolean) {
    Log.i(TAG, "Setting push registered: $registered", Throwable())

    val previous = isRegistered

    putBoolean(KEY_IS_REGISTERED, registered)

    ApplicationDependencies.getIncomingMessageObserver().notifyRegistrationChanged()

    if (previous != registered) {
      Recipient.self().live().refresh()
    }

    if (previous && !registered) {
      clearLocalCredentials(ApplicationDependencies.getApplication())
    }
  }

  private fun clearLocalCredentials(context: Context) {
    putString(KEY_SERVICE_PASSWORD, Util.getSecret(18))

    val newProfileKey = ProfileKeyUtil.createNew()
    val self = Recipient.self()

    ShadowDatabase.recipients.setProfileKey(self.id, newProfileKey)
    ApplicationDependencies.getGroupsV2Authorization().clear()
  }

  private fun migrateFromSharedPrefs(context: Context) {
    Log.i(TAG, "Migrating account values from shared prefs.")

    putString(KEY_ACI, TextSecurePreferences.getStringPreference(context, "pref_local_uuid", null))
    putString(KEY_USER_LOGIN, TextSecurePreferences.getStringPreference(context, "pref_local_number", null))
    putString(KEY_SERVICE_PASSWORD, TextSecurePreferences.getStringPreference(context, "pref_gcm_password", null))
    putBoolean(KEY_IS_REGISTERED, TextSecurePreferences.getBooleanPreference(context, "pref_gcm_registered", false))
    putInteger(KEY_REGISTRATION_ID, TextSecurePreferences.getIntegerPreference(context, "pref_local_registration_id", 0))
    putBoolean(KEY_FCM_ENABLED, !TextSecurePreferences.getBooleanPreference(context, "pref_gcm_disabled", false))
    putString(KEY_FCM_TOKEN, TextSecurePreferences.getStringPreference(context, "pref_gcm_registration_id", null))
    putInteger(KEY_FCM_TOKEN_VERSION, TextSecurePreferences.getIntegerPreference(context, "pref_gcm_registration_id_version", 0))
    putLong(KEY_FCM_TOKEN_LAST_SET_TIME, TextSecurePreferences.getLongPreference(context, "pref_gcm_registration_id_last_set_time", 0))
  }
}