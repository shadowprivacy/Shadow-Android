/*
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package su.sres.signalservice.api;

import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.util.guava.Optional;
import su.sres.signalservice.api.crypto.AttachmentCipherInputStream;
import su.sres.signalservice.api.crypto.ProfileCipherInputStream;
import su.sres.signalservice.api.crypto.UnidentifiedAccess;
import su.sres.signalservice.api.messages.SignalServiceAttachment.ProgressListener;
import su.sres.signalservice.api.messages.SignalServiceAttachmentPointer;
import su.sres.signalservice.api.messages.SignalServiceDataMessage;
import su.sres.signalservice.api.messages.SignalServiceEnvelope;
import su.sres.signalservice.api.messages.SignalServiceStickerManifest;
import su.sres.signalservice.api.profiles.SignalServiceProfile;
import su.sres.signalservice.api.push.SignalServiceAddress;
import su.sres.signalservice.api.util.CredentialsProvider;
import su.sres.signalservice.api.util.SleepTimer;
import su.sres.signalservice.api.util.UuidUtil;
import su.sres.signalservice.api.websocket.ConnectivityListener;
import su.sres.signalservice.internal.configuration.SignalServiceConfiguration;
import su.sres.signalservice.internal.push.PushServiceSocket;
import su.sres.signalservice.internal.push.SignalServiceEnvelopeEntity;
import su.sres.signalservice.internal.sticker.StickerProtos;
import su.sres.signalservice.internal.util.StaticCredentialsProvider;
import su.sres.signalservice.internal.util.Util;
import su.sres.signalservice.internal.websocket.WebSocketConnection;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * The primary interface for receiving Signal Service messages.
 *
 * @author Moxie Marlinspike
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class SignalServiceMessageReceiver {

  private final PushServiceSocket          socket;
  private final SignalServiceConfiguration urls;
  private final CredentialsProvider        credentialsProvider;
  private final String                     userAgent;
  private final ConnectivityListener       connectivityListener;
  private final SleepTimer                 sleepTimer;

  /**
   * Construct a SignalServiceMessageReceiver.
   *
   * @param urls The URL of the Signal Service.
   * @param uuid The Signal Service UUID.
   * @param e164 The Signal Service phone number.
   * @param password The Signal Service user password.
   * @param signalingKey The 52 byte signaling key assigned to this user at registration.
   */
  public SignalServiceMessageReceiver(SignalServiceConfiguration urls,
                                      UUID uuid, String e164, String password,
                                      String signalingKey, String userAgent,
                                      ConnectivityListener listener,
                                      SleepTimer timer)
  {
    this(urls, new StaticCredentialsProvider(uuid, e164, password, signalingKey), userAgent, listener, timer);
  }

  /**
   * Construct a SignalServiceMessageReceiver.
   *
   * @param urls The URL of the Signal Service.
   * @param credentials The Signal Service user's credentials.
   */
  public SignalServiceMessageReceiver(SignalServiceConfiguration urls,
                                      CredentialsProvider credentials,
                                      String userAgent,
                                      ConnectivityListener listener,
                                      SleepTimer timer)
  {
    this.urls                 = urls;
    this.credentialsProvider  = credentials;
    this.socket               = new PushServiceSocket(urls, credentials, userAgent);
    this.userAgent            = userAgent;
    this.connectivityListener = listener;
    this.sleepTimer           = timer;
  }

  /**
   * Retrieves a SignalServiceAttachment.
   *
   * @param pointer The {@link SignalServiceAttachmentPointer}
   *                received in a {@link SignalServiceDataMessage}.
   * @param destination The download destination for this attachment.
   *
   * @return An InputStream that streams the plaintext attachment contents.
   * @throws IOException
   * @throws InvalidMessageException
   */
  public InputStream retrieveAttachment(SignalServiceAttachmentPointer pointer, File destination, int maxSizeBytes)
      throws IOException, InvalidMessageException
  {
    return retrieveAttachment(pointer, destination, maxSizeBytes, null);
  }

  public SignalServiceProfile retrieveProfile(SignalServiceAddress address, Optional<UnidentifiedAccess> unidentifiedAccess)
    throws IOException
  {
    return socket.retrieveProfile(address, unidentifiedAccess);
  }

  public SignalServiceProfile retrieveProfileByUsername(String username, Optional<UnidentifiedAccess> unidentifiedAccess)
          throws IOException
  {
    return socket.retrieveProfileByUsername(username, unidentifiedAccess);
  }

  public InputStream retrieveProfileAvatar(String path, File destination, byte[] profileKey, int maxSizeBytes)
    throws IOException
  {
    socket.retrieveProfileAvatar(path, destination, maxSizeBytes);
    return new ProfileCipherInputStream(new FileInputStream(destination), profileKey);
  }

  /**
   * Retrieves a SignalServiceAttachment.
   *
   * @param pointer The {@link SignalServiceAttachmentPointer}
   *                received in a {@link SignalServiceDataMessage}.
   * @param destination The download destination for this attachment.
   * @param listener An optional listener (may be null) to receive callbacks on download progress.
   *
   * @return An InputStream that streams the plaintext attachment contents.
   * @throws IOException
   * @throws InvalidMessageException
   */
  public InputStream retrieveAttachment(SignalServiceAttachmentPointer pointer, File destination, int maxSizeBytes, ProgressListener listener)
      throws IOException, InvalidMessageException
  {
    if (!pointer.getDigest().isPresent()) throw new InvalidMessageException("No attachment digest!");

    socket.retrieveAttachment(pointer.getId(), destination, maxSizeBytes, listener);
    return AttachmentCipherInputStream.createForAttachment(destination, pointer.getSize().or(0), pointer.getKey(), pointer.getDigest().get());
  }

  public InputStream retrieveSticker(byte[] packId, byte[] packKey, int stickerId)
          throws IOException, InvalidMessageException
  {
    byte[] data = socket.retrieveSticker(packId, stickerId);
    return AttachmentCipherInputStream.createForStickerData(data, packKey);
  }

  /**
   * Retrieves a {@link SignalServiceStickerManifest}.
   *
   * @param packId The 16-byte packId that identifies the sticker pack.
   * @param packKey The 32-byte packKey that decrypts the sticker pack.
   * @return The {@link SignalServiceStickerManifest} representing the sticker pack.
   * @throws IOException
   * @throws InvalidMessageException
   */
  public SignalServiceStickerManifest retrieveStickerManifest(byte[] packId, byte[] packKey)
          throws IOException, InvalidMessageException
  {
    byte[] manifestBytes = socket.retrieveStickerManifest(packId);

    InputStream           cipherStream = AttachmentCipherInputStream.createForStickerData(manifestBytes, packKey);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    Util.copy(cipherStream, outputStream);

    StickerProtos.Pack                             pack     = StickerProtos.Pack.parseFrom(outputStream.toByteArray());
    List<SignalServiceStickerManifest.StickerInfo> stickers = new ArrayList<>(pack.getStickersCount());
    SignalServiceStickerManifest.StickerInfo       cover    = pack.hasCover() ? new SignalServiceStickerManifest.StickerInfo(pack.getCover().getId(), pack.getCover().getEmoji())
            : null;

    for (StickerProtos.Pack.Sticker sticker : pack.getStickersList()) {
      stickers.add(new SignalServiceStickerManifest.StickerInfo(sticker.getId(), sticker.getEmoji()));
    }

    return new SignalServiceStickerManifest(pack.getTitle(), pack.getAuthor(), cover, stickers);
  }

  /**
   * Creates a pipe for receiving SignalService messages.
   *
   * Callers must call {@link SignalServiceMessagePipe#shutdown()} when finished with the pipe.
   *
   * @return A SignalServiceMessagePipe for receiving Signal Service messages.
   */
  public SignalServiceMessagePipe createMessagePipe() {
    WebSocketConnection webSocket = new WebSocketConnection(urls.getSignalServiceUrls()[0].getUrl(),
                                                            urls.getSignalServiceUrls()[0].getTrustStore(),
                                                            Optional.of(credentialsProvider), userAgent, connectivityListener,
                                                            sleepTimer);

    return new SignalServiceMessagePipe(webSocket, Optional.of(credentialsProvider));
  }

  public SignalServiceMessagePipe createUnidentifiedMessagePipe() {
    WebSocketConnection webSocket = new WebSocketConnection(urls.getSignalServiceUrls()[0].getUrl(),
                                                            urls.getSignalServiceUrls()[0].getTrustStore(),
                                                            Optional.<CredentialsProvider>absent(), userAgent, connectivityListener,
                                                            sleepTimer);

    return new SignalServiceMessagePipe(webSocket, Optional.of(credentialsProvider));
  }

  public List<SignalServiceEnvelope> retrieveMessages() throws IOException {
    return retrieveMessages(new NullMessageReceivedCallback());
  }

  public List<SignalServiceEnvelope> retrieveMessages(MessageReceivedCallback callback)
      throws IOException
  {
    List<SignalServiceEnvelope>       results  = new LinkedList<>();
    List<SignalServiceEnvelopeEntity> entities = socket.getMessages();

    for (SignalServiceEnvelopeEntity entity : entities) {
      SignalServiceEnvelope envelope;

      if (entity.hasSource() && entity.getSourceDevice() > 0) {
        SignalServiceAddress address = new SignalServiceAddress(UuidUtil.parseOrNull(entity.getSourceUuid()), entity.getSourceE164());
        envelope = new SignalServiceEnvelope(entity.getType(), Optional.of(address),
                entity.getSourceDevice(), entity.getTimestamp(),
                entity.getMessage(), entity.getContent(),
                entity.getServerTimestamp(), entity.getServerUuid());
      } else {
        envelope = new SignalServiceEnvelope(entity.getType(), entity.getTimestamp(),
                entity.getMessage(), entity.getContent(),
                entity.getServerTimestamp(), entity.getServerUuid());
      }

      callback.onMessage(envelope);
      results.add(envelope);

      if (envelope.hasUuid()) socket.acknowledgeMessage(envelope.getUuid());
      else                    socket.acknowledgeMessage(entity.getSourceE164(), entity.getTimestamp());
    }

    return results;
  }

  public void setSoTimeoutMillis(long soTimeoutMillis) {
    socket.setSoTimeoutMillis(soTimeoutMillis);
  }

  public interface MessageReceivedCallback {
    public void onMessage(SignalServiceEnvelope envelope);
  }

  public static class NullMessageReceivedCallback implements MessageReceivedCallback {
    @Override
    public void onMessage(SignalServiceEnvelope envelope) {}
  }

}