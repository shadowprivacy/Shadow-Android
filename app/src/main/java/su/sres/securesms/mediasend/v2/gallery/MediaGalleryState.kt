package su.sres.securesms.mediasend.v2.gallery

import su.sres.securesms.util.MappingModel

data class MediaGalleryState(
  val bucketId: String?,
  val bucketTitle: String?,
  val items: List<MappingModel<*>> = listOf()
)