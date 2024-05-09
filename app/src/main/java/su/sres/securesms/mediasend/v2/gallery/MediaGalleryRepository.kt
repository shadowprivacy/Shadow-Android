package su.sres.securesms.mediasend.v2.gallery

import android.content.Context
import su.sres.securesms.mediasend.Media
import su.sres.securesms.mediasend.MediaFolder
import su.sres.securesms.mediasend.MediaRepository

class MediaGalleryRepository(context: Context, private val mediaRepository: MediaRepository) {
  private val context: Context = context.applicationContext

  fun getFolders(onFoldersRetrieved: (List<MediaFolder>) -> Unit) {
    mediaRepository.getFolders(context) { onFoldersRetrieved(it) }
  }

  fun getMedia(bucketId: String, onMediaRetrieved: (List<Media>) -> Unit) {
    mediaRepository.getMediaInBucket(context, bucketId) { onMediaRetrieved(it) }
  }
}