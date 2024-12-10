package su.sres.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.signal.zkgroup.receipts.ReceiptCredentialRequest;
import su.sres.util.Base64;

class BoostReceiptCredentialRequestJson {
  @JsonProperty("paymentIntentId")
  private final String paymentIntentId;

  @JsonProperty("receiptCredentialRequest")
  private final String receiptCredentialRequest;

  BoostReceiptCredentialRequestJson(String paymentIntentId, ReceiptCredentialRequest receiptCredentialRequest) {
    this.paymentIntentId          = paymentIntentId;
    this.receiptCredentialRequest = Base64.encodeBytes(receiptCredentialRequest.serialize());
  }
}
