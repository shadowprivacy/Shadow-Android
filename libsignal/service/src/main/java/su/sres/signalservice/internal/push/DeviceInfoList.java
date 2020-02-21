package su.sres.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

import su.sres.signalservice.api.messages.multidevice.DeviceInfo;

import java.util.List;

public class DeviceInfoList {

  @JsonProperty
  private List<DeviceInfo> devices;

  public DeviceInfoList() {}

  public List<DeviceInfo> getDevices() {
    return devices;
  }
}
