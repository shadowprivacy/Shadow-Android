package su.sres.securesms.crypto;

import android.content.Context;
import androidx.annotation.NonNull;

import su.sres.securesms.crypto.storage.TextSecureSessionStore;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientId;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.state.SessionStore;
import su.sres.signalservice.api.push.SignalServiceAddress;

public class SessionUtil {

  public static boolean hasSession(@NonNull RecipientId id) {
    SignalProtocolAddress axolotlAddress = new SignalProtocolAddress(Recipient.resolved(id).requireServiceId(), SignalServiceAddress.DEFAULT_DEVICE_ID);

    return ApplicationDependencies.getSessionStore().containsSession(axolotlAddress);
  }

  public static void archiveSiblingSessions(SignalProtocolAddress address) {
    ApplicationDependencies.getSessionStore().archiveSiblingSessions(address);
  }

  public static void archiveAllSessions() {
    ApplicationDependencies.getSessionStore().archiveAllSessions();
  }

  public static void archiveSession(RecipientId recipientId, int deviceId) {
    ApplicationDependencies.getSessionStore().archiveSession(recipientId, deviceId);
  }

  public static boolean ratchetKeyMatches(@NonNull Recipient recipient, int deviceId, @NonNull ECPublicKey ratchetKey) {
    SignalProtocolAddress address = new SignalProtocolAddress(recipient.resolve().requireServiceId(), deviceId);
    SessionRecord         session = ApplicationDependencies.getSessionStore().loadSession(address);

    return session.currentRatchetKeyMatches(ratchetKey);
  }
}
