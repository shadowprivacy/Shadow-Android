package su.sres.signalservice.internal.push;

import su.sres.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;

public class DeviceLimitExceededException extends NonSuccessfulResponseCodeException {

  private final DeviceLimit deviceLimit;

  public DeviceLimitExceededException(DeviceLimit deviceLimit) {
    super(411);
    this.deviceLimit = deviceLimit;
  }

  public int getCurrent() {
    return deviceLimit.getCurrent();
  }

  public int getMax() {
    return deviceLimit.getMax();
  }
}
