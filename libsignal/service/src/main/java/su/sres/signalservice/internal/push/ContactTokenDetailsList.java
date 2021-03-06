/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package su.sres.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

import su.sres.signalservice.api.push.ContactTokenDetails;

import java.util.List;

public class ContactTokenDetailsList {

  @JsonProperty
  private List<ContactTokenDetails> contacts;

  public ContactTokenDetailsList() {}

  public List<ContactTokenDetails> getContacts() {
    return contacts;
  }
}
