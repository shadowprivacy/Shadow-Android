package su.sres.securesms.conversation.drafts

import android.content.Context
import android.net.Uri
import su.sres.core.util.concurrent.SignalExecutors
import su.sres.securesms.database.DraftDatabase
import su.sres.securesms.providers.BlobProvider

class DraftRepository(private val context: Context) {
  fun deleteVoiceNoteDraft(draft: DraftDatabase.Draft) {
    deleteBlob(Uri.parse(draft.value).buildUpon().clearQuery().build())
  }

  fun deleteBlob(uri: Uri) {
    SignalExecutors.BOUNDED.execute {
      BlobProvider.getInstance().delete(context, uri)
    }
  }
}