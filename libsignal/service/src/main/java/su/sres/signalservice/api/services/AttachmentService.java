package su.sres.signalservice.api.services;

import su.sres.signalservice.api.SignalWebSocket;
import su.sres.signalservice.internal.ServiceResponse;
import su.sres.signalservice.internal.ServiceResponseProcessor;
import su.sres.signalservice.internal.push.AttachmentV2UploadAttributes;
import su.sres.signalservice.internal.push.AttachmentV3UploadAttributes;
import su.sres.signalservice.internal.websocket.DefaultResponseMapper;
import su.sres.signalservice.internal.websocket.WebSocketProtos.WebSocketRequestMessage;

import java.security.SecureRandom;

import io.reactivex.rxjava3.core.Single;

/**
 * Provide WebSocket based interface to attachment upload endpoints.
 *
 * Note: To be expanded to have REST fallback and other attachment related operations.
 */
public final class AttachmentService {
  private final SignalWebSocket signalWebSocket;

  public AttachmentService(SignalWebSocket signalWebSocket) {
    this.signalWebSocket = signalWebSocket;
  }

  public Single<ServiceResponse<AttachmentV2UploadAttributes>> getAttachmentV2UploadAttributes() {
    WebSocketRequestMessage requestMessage = WebSocketRequestMessage.newBuilder()
                                                                    .setId(new SecureRandom().nextLong())
                                                                    .setVerb("GET")
                                                                    .setPath("/v2/attachments/form/upload")
                                                                    .build();

    return signalWebSocket.request(requestMessage)
                          .map(DefaultResponseMapper.getDefault(AttachmentV2UploadAttributes.class)::map)
                          .onErrorReturn(ServiceResponse::forUnknownError);
  }

  public Single<ServiceResponse<AttachmentV3UploadAttributes>> getAttachmentV3UploadAttributes() {
    WebSocketRequestMessage requestMessage = WebSocketRequestMessage.newBuilder()
                                                                    .setId(new SecureRandom().nextLong())
                                                                    .setVerb("GET")
                                                                    .setPath("/v3/attachments/form/upload")
                                                                    .build();

    return signalWebSocket.request(requestMessage)
                          .map(DefaultResponseMapper.getDefault(AttachmentV3UploadAttributes.class)::map)
                          .onErrorReturn(ServiceResponse::forUnknownError);
  }

  public static class AttachmentAttributesResponseProcessor<T> extends ServiceResponseProcessor<T> {
    public AttachmentAttributesResponseProcessor(ServiceResponse<T> response) {
      super(response);
    }
  }
}
