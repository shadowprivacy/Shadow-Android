package su.sres.securesms.util

import su.sres.securesms.recipients.Recipient
import su.sres.securesms.recipients.RecipientId
import su.sres.signalservice.api.push.ACI
import su.sres.signalservice.api.push.SignalServiceAddress
import java.lang.IllegalArgumentException

/**
 * A list of Recipients, but with some helpful methods for retrieving them by various properties. Uses lazy properties to ensure that it will be as performant
 * as a regular list if you don't call any of the extra methods.
 */
class RecipientAccessList(private val recipients: List<Recipient>) : List<Recipient> by recipients {

  private val byAci: Map<ACI, Recipient> by lazy {
    recipients
      .filter { it.hasAci() }
      .associateBy { it.requireAci() }
  }

  private val byUserLogin: Map<String, Recipient> by lazy {
    recipients
      .filter { it.hasE164() }
      .associateBy { it.requireE164() }
  }

  fun requireByAddress(address: SignalServiceAddress): Recipient {
    if (byAci.containsKey(address.aci)) {
      return byAci[address.aci]!!
    } else if (address.number.isPresent && byUserLogin.containsKey(address.number.get())) {
      return byUserLogin[address.number.get()]!!
    } else {
      throw IllegalArgumentException("Could not find a matching recipient!")
    }
  }

  fun requireIdByAddress(address: SignalServiceAddress): RecipientId {
    return requireByAddress(address).id
  }
}