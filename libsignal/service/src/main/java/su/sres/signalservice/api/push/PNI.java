package su.sres.signalservice.api.push;

import java.util.UUID;

import su.sres.signalservice.api.util.UuidUtil;

/**
 * A PNI is a "Phone Number Identity". They're just UUIDs, but given multiple different things could be UUIDs, this wrapper exists to give us type safety around
 * this *specific type* of UUID.
 */
public final class PNI extends AccountIdentifier {

  public static PNI from(UUID uuid) {
    return new PNI(uuid);
  }

  public static PNI parseOrNull(String raw) {
    UUID uuid = UuidUtil.parseOrNull(raw);
    return uuid != null ? from(uuid) : null;
  }

  private PNI(UUID uuid) {
    super(uuid);
  }

  @Override
  public int hashCode() {
    return uuid.hashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof PNI) {
      return uuid.equals(((PNI) other).uuid);
    } else {
      return false;
    }
  }
}