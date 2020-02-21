/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package su.sres.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PreKeyStatus {

  @JsonProperty
  private int count;

  public PreKeyStatus() {}

  public int getCount() {
    return count;
  }
}
