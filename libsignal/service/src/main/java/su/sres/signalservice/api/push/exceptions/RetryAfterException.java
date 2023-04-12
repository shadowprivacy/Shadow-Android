package su.sres.signalservice.api.push.exceptions;

public class RetryAfterException extends NonSuccessfulResponseCodeException {
    public RetryAfterException() {
        super(503);
    }
}