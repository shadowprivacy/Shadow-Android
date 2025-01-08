package su.sres.securesms.reactions

import android.content.Context
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.ObservableEmitter
import io.reactivex.rxjava3.schedulers.Schedulers
import su.sres.securesms.components.emoji.EmojiUtil
import su.sres.securesms.database.DatabaseObserver
import su.sres.securesms.database.ShadowDatabase
import su.sres.securesms.database.model.MessageId
import su.sres.securesms.database.model.ReactionRecord
import su.sres.securesms.dependencies.ApplicationDependencies
import su.sres.securesms.recipients.Recipient

class ReactionsRepository {

  fun getReactions(messageId: MessageId): Observable<List<ReactionDetails>> {
    return Observable.create { emitter: ObservableEmitter<List<ReactionDetails>> ->
      val databaseObserver: DatabaseObserver = ApplicationDependencies.getDatabaseObserver()

      val messageObserver = DatabaseObserver.MessageObserver { messageId ->
        emitter.onNext(fetchReactionDetails(messageId))
      }

      databaseObserver.registerMessageUpdateObserver(messageObserver)

      emitter.setCancellable {
        databaseObserver.unregisterObserver(messageObserver)
      }

      emitter.onNext(fetchReactionDetails(messageId))
    }.subscribeOn(Schedulers.io())
  }

  private fun fetchReactionDetails(messageId: MessageId): List<ReactionDetails> {
    val context: Context = ApplicationDependencies.getApplication()
    val reactions: List<ReactionRecord> = ShadowDatabase.reactions.getReactions(messageId)

    return reactions.map { reaction ->
      ReactionDetails(
        sender = Recipient.resolved(reaction.author),
        baseEmoji = EmojiUtil.getCanonicalRepresentation(reaction.emoji),
        displayEmoji = reaction.emoji,
        timestamp = reaction.dateReceived
      )
    }
  }
}