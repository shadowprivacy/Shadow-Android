package su.sres.signalservice.api.websocket;

import su.sres.signalservice.internal.websocket.WebSocketConnection;

public interface WebSocketFactory {
  WebSocketConnection createWebSocket();
  WebSocketConnection createUnidentifiedWebSocket();
}
