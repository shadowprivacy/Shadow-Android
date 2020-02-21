package su.sres.signalservice.internal.websocket;

public interface WebSocketEventListener {

  public void onMessage(byte[] payload);
  public void onClose();
  public void onConnected();

}
