/*
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package su.sres.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.stream.Collectors;

public class OutgoingPushMessageList {

  @JsonProperty
  private String destination;

  @JsonProperty
  private long timestamp;

  @JsonProperty
  private List<OutgoingPushMessage> messages;

  @JsonProperty
  private boolean online;

  public OutgoingPushMessageList(String destination,
                                 long timestamp,
                                 List<OutgoingPushMessage> messages,
                                 boolean online)
  {
    this.timestamp   = timestamp;
    this.destination = destination;
    this.messages    = messages;
    this.online      = online;
  }

  public String getDestination() {
    return destination;
  }

  public List<OutgoingPushMessage> getMessages() {
    return messages;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public boolean isOnline() {
    return online;
  }

  @JsonIgnore
  public List<Integer> getDevices() {
    return messages.stream().map(OutgoingPushMessage::getDestinationDeviceId).collect(Collectors.toList());
  }
}
