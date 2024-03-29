package su.sres.signalservice.push;

import junit.framework.TestCase;

import su.sres.signalservice.internal.push.PushTransportDetails;

public class PushTransportDetailsTest extends TestCase {

  private final PushTransportDetails transportV3 = new PushTransportDetails();

  public void testV3Padding() {
    for (int i=0;i<159;i++) {
      byte[] message = new byte[i];
      assertEquals(transportV3.getPaddedMessageBody(message).length, 159);
    }

    for (int i=159;i<319;i++) {
      byte[] message = new byte[i];
      assertEquals(transportV3.getPaddedMessageBody(message).length, 319);
    }

    for (int i=319;i<479;i++) {
      byte[] message = new byte[i];
      assertEquals(transportV3.getPaddedMessageBody(message).length, 479);
    }
  }
}
