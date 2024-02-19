package su.sres.signalservice.internal.push.http;

import su.sres.signalservice.api.messages.SendMessageResult;

/**
 * Used to let a listener know when each individual send in a collection of sends has been completed.
 */
public interface PartialSendCompleteListener {
  void onPartialSendComplete(SendMessageResult result);
}
