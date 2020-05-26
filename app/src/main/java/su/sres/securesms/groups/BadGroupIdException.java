package su.sres.securesms.groups;

public final class BadGroupIdException extends Exception {
    BadGroupIdException(String message) {
        super(message);
    }

    BadGroupIdException() {
        super();
    }

    BadGroupIdException(Exception e) {
        super(e);
    }
}