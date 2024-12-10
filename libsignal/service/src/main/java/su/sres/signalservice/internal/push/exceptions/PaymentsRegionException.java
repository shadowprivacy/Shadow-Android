package su.sres.signalservice.internal.push.exceptions;

import okhttp3.ResponseBody;
import su.sres.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;

public final class PaymentsRegionException extends NonSuccessfulResponseCodeException {
  public PaymentsRegionException(int code) {
    super(code);
  }

  /**
   * Promotes a 403 to this exception type.
   */
  public static void responseCodeHandler(int responseCode, ResponseBody body) throws PaymentsRegionException {
    if (responseCode == 403) {
      throw new PaymentsRegionException(responseCode);
    }
  }
}
