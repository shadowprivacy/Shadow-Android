package su.sres.signalservice.api.push.exceptions;

public class UsernameTakenException extends NonSuccessfulResponseCodeException {
    public UsernameTakenException() {
        super(409);
    }
}