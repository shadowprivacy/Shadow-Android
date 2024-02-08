package su.sres.signalservice.internal.push.exceptions;

import su.sres.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import su.sres.signalservice.internal.push.GroupMismatchedDevices;

import java.util.Arrays;
import java.util.List;

/**
 * Represents a 409 response from the service during a sender key send.
 */
public class GroupMismatchedDevicesException extends NonSuccessfulResponseCodeException {

  private final List<GroupMismatchedDevices> mismatchedDevices;

  public GroupMismatchedDevicesException(GroupMismatchedDevices[] mismatchedDevices) {
    super(409);
    this.mismatchedDevices = Arrays.asList(mismatchedDevices);
  }

  public List<GroupMismatchedDevices> getMismatchedDevices() {
    return mismatchedDevices;
  }
}
