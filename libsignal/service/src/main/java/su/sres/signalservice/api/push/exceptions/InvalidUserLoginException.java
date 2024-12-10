package su.sres.signalservice.api.push.exceptions;

/**
 * An exception indicating that the server believes the user login provided is invalid, whatever that means.
 */
public class InvalidUserLoginException extends NonSuccessfulResponseCodeException {
  public InvalidUserLoginException() {
    super(400);
  }
}
