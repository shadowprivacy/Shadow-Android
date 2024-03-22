package su.sres.securesms.util

import su.sres.securesms.recipients.Recipient
import su.sres.securesms.recipients.RecipientId
import su.sres.signalservice.api.push.SignalServiceAddress
import java.lang.IllegalArgumentException
import java.util.UUID

/**
 * A list of Recipients, but with some helpful methods for retrieving them by various properties. Uses lazy properties to ensure that it will be as performant
 * as a regular list if you don't call any of the extra methods.
 */
class RecipientAccessList(private val recipients: List<Recipient>) : List<Recipient> by recipients {

  private val byUuid: Map<UUID, Recipient> by lazy {
    recipients
      .filter { it.hasUuid() }
      .associateBy { it.requireUuid() }
  }

  private val byUserLogin: Map<String, Recipient> by lazy {
    recipients
      .filter { it.hasE164() }
      .associateBy { it.requireE164() }
  }

  fun requireByAddress(address: SignalServiceAddress): Recipient {
    if (byUuid.containsKey(address.uuid)) {
      return byUuid.get(address.uuid)!!
    } else if (address.number.isPresent && byUserLogin.containsKey(address.number.get())) {
      return byUserLogin.get(address.number.get())!!
    } else {
      throw IllegalArgumentException("Could not find a matching recipient!")
    }
  }

  fun requireIdByAddress(address: SignalServiceAddress): RecipientId {
    return requireByAddress(address).id
  }
}