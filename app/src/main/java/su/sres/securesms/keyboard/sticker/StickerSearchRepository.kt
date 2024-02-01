package su.sres.securesms.keyboard.sticker

import android.content.Context
import androidx.annotation.WorkerThread
import su.sres.securesms.components.emoji.EmojiUtil
import su.sres.securesms.database.DatabaseFactory
import su.sres.securesms.database.EmojiSearchDatabase
import su.sres.securesms.database.StickerDatabase
import su.sres.securesms.database.StickerDatabase.StickerRecordReader
import su.sres.securesms.database.model.StickerRecord

private const val RECENT_LIMIT = 24
private const val EMOJI_SEARCH_RESULTS_LIMIT = 20

class StickerSearchRepository(context: Context) {

  private val emojiSearchDatabase: EmojiSearchDatabase = DatabaseFactory.getEmojiSearchDatabase(context)
  private val stickerDatabase: StickerDatabase = DatabaseFactory.getStickerDatabase(context)

  @WorkerThread
  fun search(query: String): List<StickerRecord> {
    if (query.isEmpty()) {
      return StickerRecordReader(stickerDatabase.getRecentlyUsedStickers(RECENT_LIMIT)).readAll()
    }

    val maybeEmojiQuery: List<StickerRecord> = findStickersForEmoji(query)
    val searchResults: List<StickerRecord> = emojiSearchDatabase.query(query, EMOJI_SEARCH_RESULTS_LIMIT)
      .map { findStickersForEmoji(it) }
      .flatten()

    return maybeEmojiQuery + searchResults
  }

  @WorkerThread
  private fun findStickersForEmoji(emoji: String): List<StickerRecord> {
    val searchEmoji: String = EmojiUtil.getCanonicalRepresentation(emoji)

    return EmojiUtil.getAllRepresentations(searchEmoji)
      .filterNotNull()
      .map { candidate -> StickerRecordReader(stickerDatabase.getStickersByEmoji(candidate)).readAll() }
      .flatten()
  }
}

private fun StickerRecordReader.readAll(): List<StickerRecord> {
  val stickers: MutableList<StickerRecord> = mutableListOf()
  use { reader ->
    var record: StickerRecord? = reader.next
    while (record != null) {
      stickers.add(record)
      record = reader.next
    }
  }
  return stickers
}