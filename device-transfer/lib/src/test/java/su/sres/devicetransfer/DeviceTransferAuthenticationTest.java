package su.sres.devicetransfer;

import androidx.annotation.NonNull;

import org.junit.Before;
import org.junit.Test;

import su.sres.devicetransfer.DeviceTransferAuthentication.Client;
import su.sres.devicetransfer.DeviceTransferAuthentication.DeviceTransferAuthenticationException;
import su.sres.devicetransfer.DeviceTransferAuthentication.Server;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static su.sres.signalservice.test.LibSignalLibraryUtil.assumeLibSignalSupportedOnOS;

public class DeviceTransferAuthenticationTest {

  private byte[] certificate;
  private byte[] badCertificate;

  @Before
  public void ensureNativeSupported() throws KeyGenerationFailedException {
    assumeLibSignalSupportedOnOS();

    certificate    = SelfSignedIdentity.create().getX509Encoded();
    badCertificate = SelfSignedIdentity.create().getX509Encoded();
  }

  @Test
  public void testCompute_withNoChanges() throws DeviceTransferAuthenticationException {
    Client client = new Client(certificate);
    Server server = new Server(certificate, client.getCommitment());

    byte[] clientRandom = client.setServerRandomAndGetClientRandom(server.getRandom());

    server.setClientRandom(clientRandom);
    assertEquals(client.computeShortAuthenticationCode(), server.computeShortAuthenticationCode());
  }

  @Test(expected = DeviceTransferAuthenticationException.class)
  public void testServerCompute_withChangedClientCertificate() throws DeviceTransferAuthenticationException {
    Client client = new Client(badCertificate);
    Server server = new Server(certificate, client.getCommitment());

    byte[] clientRandom = client.setServerRandomAndGetClientRandom(server.getRandom());

    server.setClientRandom(clientRandom);
    server.computeShortAuthenticationCode();
  }

  @Test(expected = DeviceTransferAuthenticationException.class)
  public void testServerCompute_withChangedClientCommitment() throws DeviceTransferAuthenticationException {
    Client client = new Client(certificate);
    Server server = new Server(certificate, randomBytes());

    byte[] clientRandom = client.setServerRandomAndGetClientRandom(server.getRandom());

    server.setClientRandom(clientRandom);
    server.computeShortAuthenticationCode();
  }

  @Test(expected = DeviceTransferAuthenticationException.class)
  public void testServerCompute_withChangedClientRandom() throws DeviceTransferAuthenticationException {
    Client client = new Client(certificate);
    Server server = new Server(certificate, client.getCommitment());

    client.setServerRandomAndGetClientRandom(server.getRandom());

    server.setClientRandom(randomBytes());
    server.computeShortAuthenticationCode();
  }

  @Test
  public void testClientCompute_withChangedServerSecret() throws DeviceTransferAuthenticationException {
    Client client = new Client(certificate);
    Server server = new Server(certificate, client.getCommitment());

    byte[] clientRandom = client.setServerRandomAndGetClientRandom(randomBytes());

    server.setClientRandom(clientRandom);
    assertNotEquals(client.computeShortAuthenticationCode(), server.computeShortAuthenticationCode());
  }

  private @NonNull byte[] randomBytes() {
    byte[] bytes = new byte[32];
    new Random().nextBytes(bytes);
    return bytes;
  }
}
