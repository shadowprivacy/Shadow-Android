package su.sres.signalservice.api.messages.multidevice;


import su.sres.signalservice.api.messages.SignalServiceAttachment;

public class ContactsMessage {

  private final SignalServiceAttachment contacts;
  private final boolean                 complete;

  public ContactsMessage(SignalServiceAttachment contacts, boolean complete) {
    this.contacts = contacts;
    this.complete = complete;
  }

  public SignalServiceAttachment getContactsStream() {
    return contacts;
  }

  public boolean isComplete() {
    return complete;
  }
}
