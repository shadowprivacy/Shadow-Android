package su.sres.zkgroup.profiles;

import su.sres.zkgroup.ServerPublicParams;
import su.sres.zkgroup.VerificationFailedException;

import java.security.SecureRandom;
import java.util.UUID;

public final class ClientZkProfileOperations {
  public ClientZkProfileOperations(ServerPublicParams serverPublicParams) {
  }

  public ProfileKeyCredentialRequestContext createProfileKeyCredentialRequestContext(SecureRandom random, UUID target, ProfileKey profileKey) {
    throw new AssertionError();
  }

  public ProfileKeyCredential receiveProfileKeyCredential(ProfileKeyCredentialRequestContext requestContext, ProfileKeyCredentialResponse profileKeyCredentialResponse) throws VerificationFailedException {
    throw new AssertionError();
  }
}
