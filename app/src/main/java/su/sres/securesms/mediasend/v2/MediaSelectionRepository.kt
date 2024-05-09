package su.sres.securesms.mediasend.v2

import android.content.Context
import android.net.Uri
import androidx.annotation.WorkerThread
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import su.sres.core.util.ThreadUtil
import su.sres.core.util.logging.Log
import su.sres.securesms.TransportOption
import su.sres.securesms.database.AttachmentDatabase.TransformProperties
import su.sres.securesms.database.ThreadDatabase
import su.sres.securesms.database.model.Mention
import su.sres.securesms.imageeditor.model.EditorModel
import su.sres.securesms.mediasend.CompositeMediaTransform
import su.sres.securesms.mediasend.ImageEditorModelRenderMediaTransform
import su.sres.securesms.mediasend.Media
import su.sres.securesms.mediasend.MediaRepository
import su.sres.securesms.mediasend.MediaSendActivityResult
import su.sres.securesms.mediasend.MediaTransform
import su.sres.securesms.mediasend.MediaUploadRepository
import su.sres.securesms.mediasend.SentMediaQualityTransform
import su.sres.securesms.mediasend.VideoEditorFragment
import su.sres.securesms.mediasend.VideoTrimTransform
import su.sres.securesms.mms.MediaConstraints
import su.sres.securesms.mms.OutgoingMediaMessage
import su.sres.securesms.mms.OutgoingSecureMediaMessage
import su.sres.securesms.mms.SentMediaQuality
import su.sres.securesms.mms.Slide
import su.sres.securesms.providers.BlobProvider
import su.sres.securesms.recipients.Recipient
import su.sres.securesms.recipients.RecipientId
import su.sres.securesms.scribbles.ImageEditorFragment
import su.sres.securesms.sms.MessageSender
import su.sres.securesms.sms.MessageSender.PreUploadResult
import su.sres.securesms.util.MessageUtil
import java.util.ArrayList
import java.util.concurrent.TimeUnit

private val TAG = Log.tag(MediaSelectionRepository::class.java)

class MediaSelectionRepository(context: Context) {

  private val context: Context = context.applicationContext

  private val mediaRepository = MediaRepository()

  val uploadRepository = MediaUploadRepository(context)
  val isMetered: Observable<Boolean> = MeteredConnectivity.isMetered(context)

  fun populateAndFilterMedia(media: List<Media>, mediaConstraints: MediaConstraints, maxSelection: Int): Single<MediaValidator.FilterResult> {
    return Single.fromCallable {
      val populatedMedia = mediaRepository.getPopulatedMedia(context, media)

      MediaValidator.filterMedia(context, populatedMedia, mediaConstraints, maxSelection)
    }.subscribeOn(Schedulers.io())
  }

  /**
   * Tries to send the selected media, performing proper transformations for edited images and videos.
   */
  fun send(
    selectedMedia: List<Media>,
    stateMap: Map<Uri, Any>,
    quality: SentMediaQuality,
    message: CharSequence?,
    isViewOnce: Boolean,
    singleRecipientId: RecipientId?,
    recipientIds: List<RecipientId>,
    mentions: List<Mention>,
    transport: TransportOption
  ): Maybe<MediaSendActivityResult> {

    return Maybe.create<MediaSendActivityResult> { emitter ->
      val trimmedBody: String = if (isViewOnce) "" else message?.toString()?.trim() ?: ""
      val trimmedMentions: List<Mention> = if (isViewOnce) emptyList() else mentions
      val modelsToTransform: Map<Media, MediaTransform> = buildModelsToTransform(selectedMedia, stateMap, quality)
      val oldToNewMediaMap: Map<Media, Media> = MediaRepository.transformMediaSync(context, selectedMedia, modelsToTransform)
      val updatedMedia = oldToNewMediaMap.values.toList()

      for (media in updatedMedia) {
        Log.w(TAG, media.uri.toString() + " : " + media.transformProperties.transform { t: TransformProperties -> "" + t.isVideoTrim }.or("null"))
      }

      val singleRecipient = singleRecipientId?.let { Recipient.resolved(it) }
      if (MessageSender.isLocalSelfSend(context, singleRecipient, false)) {
        Log.i(TAG, "SMS or local self-send. Skipping pre-upload.")
        emitter.onSuccess(MediaSendActivityResult.forTraditionalSend(requireNotNull(singleRecipient).id, updatedMedia, trimmedBody, transport, isViewOnce, trimmedMentions))
      } else {
        val splitMessage = MessageUtil.getSplitMessage(context, trimmedBody, transport.calculateCharacters(trimmedBody).maxPrimaryMessageSize)
        val splitBody = splitMessage.body

        if (splitMessage.textSlide.isPresent) {
          val slide: Slide = splitMessage.textSlide.get()
          uploadRepository.startUpload(
            MediaBuilder.buildMedia(
              uri = requireNotNull(slide.uri),
              mimeType = slide.contentType,
              date = System.currentTimeMillis(),
              size = slide.fileSize,
              borderless = slide.isBorderless,
              videoGif = slide.isVideoGif
            ),
            singleRecipient
          )
        }

        uploadRepository.applyMediaUpdates(oldToNewMediaMap, singleRecipient)
        uploadRepository.updateCaptions(updatedMedia)
        uploadRepository.updateDisplayOrder(updatedMedia)
        uploadRepository.getPreUploadResults { uploadResults ->
          if (recipientIds.isNotEmpty()) {
            val recipients = recipientIds.map { Recipient.resolved(it) }
            sendMessages(recipients, splitBody, uploadResults, trimmedMentions, isViewOnce)
            uploadRepository.deleteAbandonedAttachments()
            emitter.onComplete()
          } else {
            emitter.onSuccess(MediaSendActivityResult.forPreUpload(requireNotNull(singleRecipient).id, uploadResults, splitBody, transport, isViewOnce, trimmedMentions))
          }
        }
      }
    }.subscribeOn(Schedulers.io()).cast(MediaSendActivityResult::class.java)
  }

  fun deleteBlobs(media: List<Media>) {
    media
      .map(Media::getUri)
      .filter(BlobProvider::isAuthority)
      .forEach { BlobProvider.getInstance().delete(context, it) }
  }

  fun cleanUp(selectedMedia: List<Media>) {
    deleteBlobs(selectedMedia)
    uploadRepository.cancelAllUploads()
    uploadRepository.deleteAbandonedAttachments()
  }

  fun isLocalSelfSend(recipient: Recipient?, isSms: Boolean): Boolean {
    return MessageSender.isLocalSelfSend(context, recipient, isSms)
  }

  @WorkerThread
  private fun buildModelsToTransform(
    selectedMedia: List<Media>,
    stateMap: Map<Uri, Any>,
    quality: SentMediaQuality
  ): Map<Media, MediaTransform> {
    val modelsToRender: MutableMap<Media, MediaTransform> = mutableMapOf()

    selectedMedia.forEach {
      val state = stateMap[it.uri]
      if (state is ImageEditorFragment.Data) {
        val model: EditorModel? = state.readModel()
        if (model != null && model.isChanged) {
          modelsToRender[it] = ImageEditorModelRenderMediaTransform(model)
        }
      }

      if (state is VideoEditorFragment.Data && state.isDurationEdited) {
        modelsToRender[it] = VideoTrimTransform(state)
      }

      // effectively circumvent the MQ levels in the code, treating everything as HIGH
      // if (quality == SentMediaQuality.HIGH) {
        val existingTransform: MediaTransform? = modelsToRender[it]

        modelsToRender[it] = if (existingTransform == null) {
          SentMediaQualityTransform(quality)
        } else {
          CompositeMediaTransform(existingTransform, SentMediaQualityTransform(quality))
        }
      // }
    }

    return modelsToRender
  }

  @WorkerThread
  private fun sendMessages(recipients: List<Recipient>, body: String, preUploadResults: Collection<PreUploadResult>, mentions: List<Mention>, isViewOnce: Boolean) {
    val messages: MutableList<OutgoingSecureMediaMessage> = ArrayList(recipients.size)

    for (recipient in recipients) {
      val message = OutgoingMediaMessage(
        recipient,
        body, emptyList(),
        System.currentTimeMillis(),
        -1,
        TimeUnit.SECONDS.toMillis(recipient.expiresInSeconds.toLong()),
        isViewOnce,
        ThreadDatabase.DistributionTypes.DEFAULT,
        null, emptyList(), emptyList(),
        mentions, emptyList(), emptyList()
      )
      messages.add(OutgoingSecureMediaMessage(message))

      // XXX We must do this to avoid sending out messages to the same recipient with the same
      //     sentTimestamp. If we do this, they'll be considered dupes by the receiver.
      ThreadUtil.sleep(5)
    }

    MessageSender.sendMediaBroadcast(context, messages, preUploadResults)
  }
}