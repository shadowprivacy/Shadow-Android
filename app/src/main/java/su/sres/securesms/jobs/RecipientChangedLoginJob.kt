package su.sres.securesms.jobs

import su.sres.core.util.logging.Log
import su.sres.securesms.database.DatabaseFactory
import su.sres.securesms.jobmanager.Data
import su.sres.securesms.jobmanager.Job
import su.sres.securesms.recipients.Recipient
import su.sres.securesms.recipients.RecipientId

/**
 * Insert change login update items in all threads (1:1 and group) with [recipientId].
 */
class RecipientChangedLoginJob(parameters: Parameters, private val recipientId: RecipientId) : BaseJob(parameters) {

  constructor(recipientId: RecipientId) : this(
    Parameters.Builder().setQueue("RecipientChangedLoginJob_${recipientId.toQueueKey()}").build(),
    recipientId
  )

  override fun serialize(): Data {
    return Data.Builder()
      .putString(KEY_RECIPIENT_ID, recipientId.serialize())
      .build()
  }

  override fun getFactoryKey(): String {
    return KEY
  }

  override fun onRun() {
    val recipient: Recipient = Recipient.resolved(recipientId)

    if (!recipient.isBlocked && !recipient.isGroup && !recipient.isSelf) {
      Log.i(TAG, "Writing a number change event.")
      DatabaseFactory.getSmsDatabase(context).insertLoginChangeMessages(recipient)
    } else {
      Log.i(TAG, "Number changed but not relevant. blocked: ${recipient.isBlocked} isGroup: ${recipient.isGroup} isSelf: ${recipient.isSelf}")
    }
  }

  override fun onShouldRetry(e: Exception): Boolean = false

  override fun onFailure() = Unit

  class Factory : Job.Factory<RecipientChangedLoginJob> {
    override fun create(parameters: Parameters, data: Data): RecipientChangedLoginJob {
      return RecipientChangedLoginJob(parameters, RecipientId.from(data.getString(KEY_RECIPIENT_ID)))
    }
  }

  companion object {
    const val KEY = "RecipientChangedLoginJob"

    private val TAG = Log.tag(RecipientChangedLoginJob::class.java)
    private const val KEY_RECIPIENT_ID = "recipient_id"
  }
}