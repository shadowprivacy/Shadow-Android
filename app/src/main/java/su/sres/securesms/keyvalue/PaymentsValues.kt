package su.sres.securesms.keyvalue

import androidx.annotation.WorkerThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import com.google.protobuf.InvalidProtocolBufferException
import com.mobilecoin.lib.Mnemonics
import com.mobilecoin.lib.exceptions.BadMnemonicException
import su.sres.core.util.logging.Log
import su.sres.securesms.database.ShadowDatabase
import su.sres.securesms.dependencies.ApplicationDependencies
import su.sres.securesms.payments.Balance
import su.sres.securesms.payments.Entropy
import su.sres.securesms.payments.GeographicalRestrictions
import su.sres.securesms.payments.Mnemonic
import su.sres.securesms.payments.MobileCoinLedgerWrapper
import su.sres.securesms.payments.currency.CurrencyUtil
import su.sres.securesms.payments.proto.MobileCoinLedger
import su.sres.securesms.recipients.Recipient
import su.sres.securesms.util.FeatureFlags
import su.sres.securesms.util.Util
import su.sres.signalservice.api.payments.Money
import java.lang.AssertionError
import java.lang.IllegalStateException
import java.math.BigDecimal
import java.util.Arrays
import java.util.Currency
import java.util.Locale

internal class PaymentsValues internal constructor(store: KeyValueStore) : SignalStoreValues(store) {

  companion object {
    private val TAG = Log.tag(PaymentsValues::class.java)

    private const val PAYMENTS_ENTROPY = "payments_entropy"
    private const val MOB_PAYMENTS_ENABLED = "mob_payments_enabled"
    private const val MOB_LEDGER = "mob_ledger"
    private const val PAYMENTS_CURRENT_CURRENCY = "payments_current_currency"
    private const val DEFAULT_CURRENCY_CODE = "GBP"
    private const val USER_CONFIRMED_MNEMONIC = "mob_payments_user_confirmed_mnemonic"
    private const val SHOW_ABOUT_MOBILE_COIN_INFO_CARD = "mob_payments_show_about_mobile_coin_info_card"
    private const val SHOW_ADDING_TO_YOUR_WALLET_INFO_CARD = "mob_payments_show_adding_to_your_wallet_info_card"
    private const val SHOW_CASHING_OUT_INFO_CARD = "mob_payments_show_cashing_out_info_card"
    private const val SHOW_RECOVERY_PHRASE_INFO_CARD = "mob_payments_show_recovery_phrase_info_card"
    private const val SHOW_UPDATE_PIN_INFO_CARD = "mob_payments_show_update_pin_info_card"

    private val LARGE_BALANCE_THRESHOLD = Money.mobileCoin(BigDecimal.valueOf(500))
  }

  private val liveCurrentCurrency: MutableLiveData<Currency> by lazy { MutableLiveData(currentCurrency()) }
  private val liveMobileCoinLedger: MutableLiveData<MobileCoinLedgerWrapper> by lazy { MutableLiveData(mobileCoinLatestFullLedger()) }
  private val liveMobileCoinBalance: LiveData<Balance> by lazy { Transformations.map(liveMobileCoinLedger) { obj: MobileCoinLedgerWrapper -> obj.balance } }

  public override fun onFirstEverAppLaunch() {}

  public override fun getKeysToIncludeInBackup(): List<String> {
    return listOf(
      PAYMENTS_ENTROPY,
      MOB_PAYMENTS_ENABLED,
      MOB_LEDGER,
      PAYMENTS_CURRENT_CURRENCY,
      DEFAULT_CURRENCY_CODE,
      USER_CONFIRMED_MNEMONIC,
      SHOW_ABOUT_MOBILE_COIN_INFO_CARD,
      SHOW_ADDING_TO_YOUR_WALLET_INFO_CARD,
      SHOW_CASHING_OUT_INFO_CARD,
      SHOW_RECOVERY_PHRASE_INFO_CARD,
      SHOW_UPDATE_PIN_INFO_CARD
    )
  }

  fun userConfirmedMnemonic(): Boolean {
    return store.getBoolean(USER_CONFIRMED_MNEMONIC, false)
  }

  fun setUserConfirmedMnemonic(userConfirmedMnemonic: Boolean) {
    store.beginWrite().putBoolean(USER_CONFIRMED_MNEMONIC, userConfirmedMnemonic).commit()
  }

  /**
   * Consider using [.getPaymentsAvailability] which includes feature flag and region status.
   */
  fun mobileCoinPaymentsEnabled(): Boolean {
    return getBoolean(MOB_PAYMENTS_ENABLED, false)
  }

  /**
   * Applies feature flags and region restrictions to return an enum which describes the available feature set for the user.
   */
  val paymentsAvailability: PaymentsAvailability
    get() {
      if (!SignalStore.account().isRegistered ||
        !GeographicalRestrictions.e164Allowed(Recipient.self().requireE164())
      ) {
        return PaymentsAvailability.NOT_IN_REGION
      }
      return if (FeatureFlags.payments()) {
        if (mobileCoinPaymentsEnabled()) {
          PaymentsAvailability.WITHDRAW_AND_SEND
        } else {
          PaymentsAvailability.REGISTRATION_AVAILABLE
        }
      } else {
        if (mobileCoinPaymentsEnabled()) {
          PaymentsAvailability.WITHDRAW_ONLY
        } else {
          PaymentsAvailability.DISABLED_REMOTELY
        }
      }
    }

  @WorkerThread
  fun setMobileCoinPaymentsEnabled(isMobileCoinPaymentsEnabled: Boolean) {
    if (mobileCoinPaymentsEnabled() == isMobileCoinPaymentsEnabled) {
      return
    }
    if (isMobileCoinPaymentsEnabled) {
      var entropy = paymentsEntropy
      if (entropy == null) {
        entropy = Entropy.generateNew()
        Log.i(TAG, "Generated new payments entropy")
      }
      store.beginWrite()
        .putBlob(PAYMENTS_ENTROPY, entropy.bytes)
        .putBoolean(MOB_PAYMENTS_ENABLED, true)
        .putString(PAYMENTS_CURRENT_CURRENCY, currentCurrency().currencyCode)
        .commit()
    } else {
      store.beginWrite()
        .putBoolean(MOB_PAYMENTS_ENABLED, false)
        .putBoolean(USER_CONFIRMED_MNEMONIC, false)
        .commit()
    }
    ShadowDatabase.recipients.markNeedsSync(Recipient.self().id)
    // StorageSyncHelper.scheduleSyncForDataChange()
  }

  val paymentsMnemonic: Mnemonic
    get() {
      val paymentsEntropy = paymentsEntropy ?: throw IllegalStateException("Entropy has not been set")
      return paymentsEntropy.asMnemonic()
    }

  /**
   * True if a local entropy is set, regardless of whether payments is currently enabled.
   */
  fun hasPaymentsEntropy(): Boolean {
    return paymentsEntropy != null
  }

  /**
   * Returns the local payments entropy, regardless of whether payments is currently enabled.
   *
   *
   * And null if has never been set.
   */
  val paymentsEntropy: Entropy?
    get() = Entropy.fromBytes(store.getBlob(PAYMENTS_ENTROPY, null))

  fun mobileCoinLatestBalance(): Balance {
    return mobileCoinLatestFullLedger().balance
  }

  fun liveMobileCoinLedger(): LiveData<MobileCoinLedgerWrapper> {
    return liveMobileCoinLedger
  }

  fun liveMobileCoinBalance(): LiveData<Balance> {
    return liveMobileCoinBalance
  }

  fun setCurrentCurrency(currentCurrency: Currency) {
    store.beginWrite()
      .putString(PAYMENTS_CURRENT_CURRENCY, currentCurrency.currencyCode)
      .commit()
    liveCurrentCurrency.postValue(currentCurrency)
  }

  fun currentCurrency(): Currency {
    val currencyCode = store.getString(PAYMENTS_CURRENT_CURRENCY, null)
    return if (currencyCode == null) determineCurrency() else Currency.getInstance(currencyCode)
  }

  fun liveCurrentCurrency(): MutableLiveData<Currency> {
    return liveCurrentCurrency
  }

  fun showAboutMobileCoinInfoCard(): Boolean {
    return store.getBoolean(SHOW_ABOUT_MOBILE_COIN_INFO_CARD, true)
  }

  fun showAddingToYourWalletInfoCard(): Boolean {
    return store.getBoolean(SHOW_ADDING_TO_YOUR_WALLET_INFO_CARD, true)
  }

  fun showCashingOutInfoCard(): Boolean {
    return store.getBoolean(SHOW_CASHING_OUT_INFO_CARD, true)
  }

  fun showRecoveryPhraseInfoCard(): Boolean {
    return if (userHasLargeBalance()) {
      store.getBoolean(SHOW_CASHING_OUT_INFO_CARD, true)
    } else {
      false
    }
  }

  fun showUpdatePinInfoCard(): Boolean {
    return false
    // if (userHasLargeBalance() &&
    //  SignalStore.kbsValues().hasPin() &&
    //  !SignalStore.kbsValues().hasOptedOut() && SignalStore.pinValues().keyboardType == PinKeyboardType.NUMERIC
    // ) {
    //  store.getBoolean(SHOW_CASHING_OUT_INFO_CARD, true)
    // } else {
    //  false
    // }
  }

  fun dismissAboutMobileCoinInfoCard() {
    store.beginWrite()
      .putBoolean(SHOW_ABOUT_MOBILE_COIN_INFO_CARD, false)
      .apply()
  }

  fun dismissAddingToYourWalletInfoCard() {
    store.beginWrite()
      .putBoolean(SHOW_ADDING_TO_YOUR_WALLET_INFO_CARD, false)
      .apply()
  }

  fun dismissCashingOutInfoCard() {
    store.beginWrite()
      .putBoolean(SHOW_CASHING_OUT_INFO_CARD, false)
      .apply()
  }

  fun dismissRecoveryPhraseInfoCard() {
    store.beginWrite()
      .putBoolean(SHOW_RECOVERY_PHRASE_INFO_CARD, false)
      .apply()
  }

  fun dismissUpdatePinInfoCard() {
    store.beginWrite()
      .putBoolean(SHOW_UPDATE_PIN_INFO_CARD, false)
      .apply()
  }

  fun setMobileCoinFullLedger(ledger: MobileCoinLedgerWrapper) {
    store.beginWrite()
      .putBlob(MOB_LEDGER, ledger.serialize())
      .commit()
    liveMobileCoinLedger.postValue(ledger)
  }

  fun mobileCoinLatestFullLedger(): MobileCoinLedgerWrapper {
    val blob = store.getBlob(MOB_LEDGER, null) ?: return MobileCoinLedgerWrapper(MobileCoinLedger.getDefaultInstance())
    return try {
      MobileCoinLedgerWrapper(MobileCoinLedger.parseFrom(blob))
    } catch (e: InvalidProtocolBufferException) {
      Log.w(TAG, "Bad cached ledger, clearing", e)
      setMobileCoinFullLedger(MobileCoinLedgerWrapper(MobileCoinLedger.getDefaultInstance()))
      throw AssertionError(e)
    }
  }

  private fun determineCurrency(): Currency {
    val localE164: String = SignalStore.account().userLogin ?: ""

    return Util.firstNonNull(
      CurrencyUtil.getCurrencyByE164(localE164),
      CurrencyUtil.getCurrencyByLocale(Locale.getDefault()),
      Currency.getInstance(DEFAULT_CURRENCY_CODE)
    )
  }

  /**
   * Does not trigger a storage sync.
   */
  fun setEnabledAndEntropy(enabled: Boolean, entropy: Entropy?) {
    val writer = store.beginWrite()

    if (entropy != null) {
      writer.putBlob(PAYMENTS_ENTROPY, entropy.bytes)
    }

    writer.putBoolean(MOB_PAYMENTS_ENABLED, enabled).commit()
  }

  @WorkerThread
  fun restoreWallet(mnemonic: String): WalletRestoreResult {
    val entropyFromMnemonic: ByteArray = try {
      Mnemonics.bip39EntropyFromMnemonic(mnemonic)
    } catch (e: BadMnemonicException) {
      return WalletRestoreResult.MNEMONIC_ERROR
    }

    val paymentsEntropy = paymentsEntropy

    if (paymentsEntropy != null) {
      val existingEntropy = paymentsEntropy.bytes
      if (Arrays.equals(existingEntropy, entropyFromMnemonic)) {
        setMobileCoinPaymentsEnabled(true)
        setUserConfirmedMnemonic(true)
        return WalletRestoreResult.ENTROPY_UNCHANGED
      }
    }

    store.beginWrite()
      .putBlob(PAYMENTS_ENTROPY, entropyFromMnemonic)
      .putBoolean(MOB_PAYMENTS_ENABLED, true)
      .remove(MOB_LEDGER)
      .putBoolean(USER_CONFIRMED_MNEMONIC, true)
      .commit()

    liveMobileCoinLedger.postValue(MobileCoinLedgerWrapper(MobileCoinLedger.getDefaultInstance()))
    // StorageSyncHelper.scheduleSyncForDataChange()

    return WalletRestoreResult.ENTROPY_CHANGED
  }

  enum class WalletRestoreResult {
    ENTROPY_CHANGED, ENTROPY_UNCHANGED, MNEMONIC_ERROR
  }

  private fun userHasLargeBalance(): Boolean {
    return mobileCoinLatestBalance().fullAmount.requireMobileCoin().greaterThan(LARGE_BALANCE_THRESHOLD)
  }
}