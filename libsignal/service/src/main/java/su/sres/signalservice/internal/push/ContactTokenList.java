/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package su.sres.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class ContactTokenList {

  @JsonProperty
  private List<String> contacts;

  public ContactTokenList(List<String> contacts) {
    this.contacts = contacts;
  }

  public ContactTokenList() {}

  public List<String> getContacts() {
    return contacts;
  }
}
